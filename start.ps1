#Requires -Version 5.1
<#
.SYNOPSIS
    ping-permission 一键构建 + Docker 启动（Windows / PowerShell 版，等价于 start.sh）

.DESCRIPTION
    .\start.ps1                 构建后端/前端并启动全部容器
    .\start.ps1 --skip-build    跳过构建，直接用已有产物启动
    .\start.ps1 down            停止并移除容器（保留数据卷）
    .\start.ps1 clean           停止并移除容器 + 数据卷（数据库会重新初始化）
    .\start.ps1 restart         重启全部容器
    .\start.ps1 logs [服务名]    查看日志（默认全部）
    .\start.ps1 status          查看容器状态
    .\start.ps1 build           只构建，不启动
    .\start.ps1 version         打印当前版本（取自根 pom.xml 的 <revision>）

    与 start.sh 的差异：构建走 Windows 版 Maven Wrapper（mvnw.cmd），不依赖 Git Bash。
    本脚本不使用 param()，参数全部走 $args，因此 --skip-build 这类写法不会被当作参数名。
#>

# 原生命令（docker / mvn / npm）的进度和告警都走 stderr，
# Windows PowerShell 5.1 在 Stop 模式下会把 stderr 当成异常抛出，因此这里用 Continue，
# 成败一律通过 $LASTEXITCODE 判断（见 Invoke-Native）。
$ErrorActionPreference = "Continue"
$RootDir = $PSScriptRoot
Set-Location $RootDir

# 读取本地配置（可选）：端口、密码等，格式与 start.sh 相同（KEY=VALUE）
$envFile = Join-Path $RootDir ".env"
if (Test-Path $envFile) {
    foreach ($line in Get-Content -LiteralPath $envFile) {
        $text = $line.Trim()
        if ($text -eq "" -or $text.StartsWith("#")) { continue }
        $index = $text.IndexOf("=")
        if ($index -lt 1) { continue }
        $key = $text.Substring(0, $index).Trim()
        $value = $text.Substring($index + 1).Trim().Trim('"').Trim("'")
        [Environment]::SetEnvironmentVariable($key, $value, "Process")
    }
}

$AppEnv = if ($env:APP_ENV) { $env:APP_ENV } else { "dev" }
$WebPort = if ($env:WEB_HOST_PORT) { $env:WEB_HOST_PORT } else { "5173" }
$GatewayPort = if ($env:GATEWAY_HOST_PORT) { $env:GATEWAY_HOST_PORT } else { "9999" }
$ConsolePort = if ($env:NACOS_CONSOLE_HOST_PORT) { $env:NACOS_CONSOLE_HOST_PORT } else { "18080" }
$MysqlPort = if ($env:MYSQL_HOST_PORT) { $env:MYSQL_HOST_PORT } else { "13306" }
$MysqlPassword = if ($env:MYSQL_ROOT_PASSWORD) { $env:MYSQL_ROOT_PASSWORD } else { "123456" }
$MysqlDatabase = if ($env:MYSQL_DATABASE) { $env:MYSQL_DATABASE } else { "ping" }
$RedisPort = if ($env:REDIS_HOST_PORT) { $env:REDIS_HOST_PORT } else { "16379" }

function Write-Log  { param([string]$Message) Write-Host "[ping] $Message" -ForegroundColor Cyan }
function Write-Warn { param([string]$Message) Write-Host "[ping] $Message" -ForegroundColor Yellow }
function Fail {
    param([string]$Message)
    Write-Host "[ping] $Message" -ForegroundColor Red
    exit 1
}

# 镜像 tag 与 Maven 版本同源：都取根 pom.xml 的 <revision>，.env 里的 IMAGE_TAG 可覆盖
function Get-ImageTag {
    $pom = Join-Path $RootDir "pom.xml"
    $match = Select-String -LiteralPath $pom -Pattern '<revision>\s*(.*?)\s*</revision>' |
        Select-Object -First 1
    if (-not $match) { return "" }
    return $match.Matches[0].Groups[1].Value
}

$script:ImageTag = if ($env:IMAGE_TAG) { $env:IMAGE_TAG } else { Get-ImageTag }
if ([string]::IsNullOrWhiteSpace($script:ImageTag)) { Fail "未能从 pom.xml 读取 <revision>，请检查根 POM" }
$env:IMAGE_TAG = $script:ImageTag

function Assert-Docker {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        Fail "未找到 docker，请先安装 Docker Desktop"
    }
    & docker info *> $null
    if ($LASTEXITCODE -ne 0) { Fail "Docker 未运行，请先启动 Docker Desktop" }
    & docker compose version *> $null
    if ($LASTEXITCODE -ne 0) { Fail "未找到 docker compose（需要 Docker Compose v2）" }
}

# Maven 不要求预装：优先用本机的 mvn，找不到时回退到仓库自带的 Wrapper
$script:MvnCmd = $null
function Resolve-Maven {
    if ($script:MvnCmd) { return $script:MvnCmd }
    if (Get-Command mvn -ErrorAction SilentlyContinue) {
        $script:MvnCmd = "mvn"
    }
    elseif (Test-Path (Join-Path $RootDir "mvnw.cmd")) {
        $script:MvnCmd = Join-Path $RootDir "mvnw.cmd"
        Write-Log "未找到 mvn，改用仓库自带的 Maven Wrapper（首次运行需联网下载 Maven）"
    }
    else {
        Fail "未找到 mvn，请安装 Maven 3.9+，或确认仓库根目录的 mvnw.cmd 存在"
    }
    return $script:MvnCmd
}

# npm 在 PowerShell 里会先命中 npm.ps1，执行策略受限时会直接报错，因此显式用 npm.cmd
function Resolve-Npm {
    if (Get-Command npm.cmd -ErrorAction SilentlyContinue) { return "npm.cmd" }
    if (Get-Command npm -ErrorAction SilentlyContinue) { return "npm" }
    Fail "未找到 npm，请先安装 Node.js 20.19+ / 22.13+"
}

function Invoke-Native {
    param([string]$Exe, [string[]]$Arguments)
    & $Exe @Arguments
    if ($LASTEXITCODE -ne 0) {
        Fail "命令失败（退出码 $LASTEXITCODE）：$Exe $($Arguments -join ' ')"
    }
}

function Assert-Jdk {
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
        Fail "未找到 java，请先安装 JDK 21（Maven Wrapper 同样需要 JDK）"
    }
    $versionLine = (& java -version 2>&1 | Select-Object -First 1)
    Write-Log "JDK: $versionLine"
}

function Build-Backend {
    $mvn = Resolve-Maven
    Write-Log "构建后端（Maven，跳过测试）..."
    # Boot 4.1 起 -DskipTests 不再跳过测试的 AOT 处理，打包用 maven.test.skip 更彻底
    Invoke-Native $mvn @("-B", "-Dmaven.test.skip=true", "clean", "package")
}

function Build-Frontend {
    $npm = Resolve-Npm
    Write-Log "构建前端（Vite）..."
    Push-Location (Join-Path $RootDir "ping-web")
    try {
        if (-not (Test-Path "node_modules")) {
            Invoke-Native $npm @("ci", "--no-audit", "--no-fund")
        }
        Invoke-Native $npm @("run", "build")
    }
    finally {
        Pop-Location
    }
}

function Assert-BuildArtifacts {
    $missing = @()
    foreach ($relative in @(
            "ping-gateway/gateway-service/target/gateway-service.jar",
            "ping-user/user-service/target/user-service.jar",
            "examples/ping-order/order-service/target/order-service.jar",
            "ping-web/dist")) {
        if (-not (Test-Path (Join-Path $RootDir $relative))) { $missing += $relative }
    }
    if ($missing.Count -gt 0) {
        Fail "缺少构建产物，请先执行完整构建：`n  $($missing -join "`n  ")"
    }
}

function Print-Urls {
    Write-Host ""
    Write-Host "  ping-permission 已启动（环境：$AppEnv，版本：$script:ImageTag）" -ForegroundColor Green
    Write-Host ""
    Write-Host "     前端页面      http://localhost:$WebPort"
    Write-Host "     网关 API      http://localhost:$GatewayPort"
    Write-Host "     Nacos 控制台   http://localhost:$ConsolePort/   (Nacos 3.x 控制台在根路径)"
    Write-Host "     MySQL         localhost:$MysqlPort   (root/$MysqlPassword, 数据库 $MysqlDatabase)"
    Write-Host "     Redis         localhost:$RedisPort"
    Write-Host ""
    Write-Host "     默认账号      admin / 123456   （普通用户 tom / 123456）"
    Write-Host ""
    Write-Host "     查看日志      .\start.ps1 logs"
    Write-Host "     停止服务      .\start.ps1 down"
    Write-Host ""
}

function Show-Help {
    Write-Host @"
用法：.\start.ps1 [命令] [参数]

  up（默认）      构建后端/前端并启动全部容器
  --skip-build    跳过构建，直接用已有产物启动
  down            停止并移除容器（保留数据卷）
  clean           停止并移除容器 + 数据卷（数据库会重新初始化）
  restart         重启全部容器
  logs [服务名]    查看日志（默认全部）
  status          查看容器状态
  build           只构建，不启动
  version         打印当前版本
"@
}

$commandArgs = @($args)
$command = if ($commandArgs.Count -gt 0) { $commandArgs[0] } else { "up" }
$extra = @()
if ($commandArgs.Count -gt 1) { $extra = $commandArgs[1..($commandArgs.Count - 1)] }

# 允许 .\start.ps1 --skip-build，等价于 .\start.ps1 up --skip-build
if ($command -eq "--skip-build") {
    $extra = @("--skip-build") + $extra
    $command = "up"
}

$skipBuild = $false
$rest = @()
foreach ($item in $extra) {
    if ($item -eq "--skip-build") { $skipBuild = $true } else { $rest += $item }
}

switch ($command.ToLowerInvariant()) {
    "up" {
        if ($rest.Count -gt 0) { Fail "未知参数: $($rest -join ' ')" }
        Assert-Docker
        if ($skipBuild) {
            Write-Warn "跳过构建，直接使用现有 target/*.jar 与 ping-web/dist"
            Assert-BuildArtifacts
        }
        else {
            Assert-Jdk
            Build-Backend
            Build-Frontend
        }
        Write-Log "构建镜像并启动容器..."
        Invoke-Native "docker" @("compose", "up", "-d", "--build")
        Print-Urls
    }
    "down" {
        Assert-Docker
        Invoke-Native "docker" @("compose", "down")
        Write-Log "已停止（数据卷保留，如需清空请执行 .\start.ps1 clean）"
    }
    "clean" {
        Assert-Docker
        Invoke-Native "docker" @("compose", "down", "-v")
        Write-Log "已停止并删除数据卷"
    }
    "restart" {
        Assert-Docker
        Invoke-Native "docker" @("compose", "restart")
    }
    "logs" {
        Assert-Docker
        Invoke-Native "docker" (@("compose", "logs", "-f", "--tail=200") + $rest)
    }
    "status" {
        Assert-Docker
        Invoke-Native "docker" @("compose", "ps")
    }
    "ps" {
        Assert-Docker
        Invoke-Native "docker" @("compose", "ps")
    }
    "build" {
        Assert-Docker
        Assert-Jdk
        Build-Backend
        Build-Frontend
        Invoke-Native "docker" @("compose", "build")
        Write-Log "构建完成"
    }
    "version" {
        Write-Output $script:ImageTag
    }
    { $_ -in @("-h", "--help", "help") } {
        Show-Help
    }
    default {
        Fail "未知命令: $command（可用: up | down | clean | restart | logs | status | build | version）"
    }
}
