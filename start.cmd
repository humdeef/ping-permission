@echo off
REM Windows 命令行入口：绕过 PowerShell 执行策略，调用同目录的 start.ps1
REM 用法与 start.sh 一致，例如：start.cmd up、start.cmd down、start.cmd --skip-build
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1" %*
exit /b %ERRORLEVEL%
