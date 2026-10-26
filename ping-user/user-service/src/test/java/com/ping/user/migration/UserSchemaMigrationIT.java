package com.ping.user.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户域 Flyway 迁移集成测试：在真实 MySQL 容器里执行 {@code db/migration}。
 *
 * <p>覆盖三类场景，它们是「共用 ping 库 + 每个服务独立历史表」这套方案的前提：</p>
 * <ol>
 *   <li>空库首次启动：V1 建出全部表并写入种子数据，V2 修正种子账号的密码；</li>
 *   <li>重复启动：不会重复执行迁移，也不会重复插入种子数据；</li>
 *   <li>存量库（没有迁移历史）：先打 baseline 0，再补执行全部版本，旧表保留。</li>
 * </ol>
 *
 * <p>命名以 IT 结尾，由 failsafe 在 {@code mvn verify} 阶段执行，不进 {@code mvn test}；
 * 没有 Docker 的环境会整体跳过。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
class UserSchemaMigrationIT {

    /** 与 docker-compose.yml 中的 MySQL 大版本保持一致。 */
    private static final String IMAGE = "mysql:8.0";

    /** 与 Nacos 中 user-service 的 spring.flyway.table 保持一致。 */
    private static final String HISTORY_TABLE = "flyway_schema_history_user";

    /** 独立的测试库：同一个容器里互不干扰，测试方法之间也就没有执行顺序依赖。 */
    private static final String DB_FRESH = "ping_it_fresh";
    private static final String DB_RERUN = "ping_it_rerun";
    private static final String DB_BASELINE = "ping_it_baseline";

    /** V1 应当建出的表（不含 Flyway 自己的历史表）。 */
    private static final Set<String> EXPECTED_TABLES = Set.of(
            "app_user", "app_user_identity",
            "sys_api_resource", "sys_api_whitelist",
            "sys_dept", "sys_login_log", "sys_operation_log",
            "sys_permission", "sys_role", "sys_role_dept", "sys_role_permission",
            "sys_user", "sys_user_role");

    /**
     * 种子数据行数。这些数字变了说明 V1 的内容被改动过：
     * 已发布的迁移文件视为只读，需要改数据请新增 V2__xxx.sql。
     */
    private static final Map<String, Integer> EXPECTED_SEEDS = Map.of(
            "app_user", 1,
            "app_user_identity", 1,
            "sys_api_resource", 47,
            "sys_api_whitelist", 3,
            "sys_dept", 6,
            "sys_permission", 43,
            "sys_role", 5,
            "sys_role_permission", 99,
            "sys_user", 4,
            "sys_user_role", 4);

    /** 种子账号统一使用的默认密码，V2 会把 lisa 也改成它。 */
    private static final String DEFAULT_PASSWORD = "123456";

    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse(IMAGE))
            .withDatabaseName("ping")
            .withUsername("ping")
            .withPassword("ping");

    @BeforeAll
    static void prepareDatabases() throws SQLException {
        createDatabase(DB_FRESH);
        createDatabase(DB_RERUN);
        createDatabase(DB_BASELINE);
    }

    @Test
    @DisplayName("空库：V1 建表写种子数据，V2 修正种子密码")
    void migratesEmptyDatabase() throws SQLException {
        MigrateResult result = flyway(DB_FRESH).migrate();

        assertEquals(3, result.migrationsExecuted, "空库应当依次执行 V1、V2、V3");
        assertEquals(EXPECTED_TABLES, tableNames(DB_FRESH));
        assertEquals(EXPECTED_SEEDS, rowCounts(DB_FRESH));
        assertTrue(new BCryptPasswordEncoder().matches(DEFAULT_PASSWORD, passwordOf(DB_FRESH, "lisa")),
                "V2 应当把 lisa 的密码重置为默认密码");
        assertEquals(List.of("1|SQL|1", "2|SQL|1", "3|SQL|1"), history(DB_FRESH), "空库不产生 BASELINE 记录");
    }

    @Test
    @DisplayName("重复启动：不会重复执行迁移，也不会重复插入种子数据")
    void secondRunIsNoOp() throws SQLException {
        assertEquals(3, flyway(DB_RERUN).migrate().migrationsExecuted);

        MigrateResult second = flyway(DB_RERUN).migrate();

        assertEquals(0, second.migrationsExecuted, "再次启动应当是空操作");
        assertEquals(EXPECTED_TABLES, tableNames(DB_RERUN));
        assertEquals(EXPECTED_SEEDS, rowCounts(DB_RERUN), "INSERT IGNORE 不应产生重复数据");
        assertEquals(List.of("1|SQL|1", "2|SQL|1", "3|SQL|1"), history(DB_RERUN));
    }

    @Test
    @DisplayName("存量库：先打 baseline 0，再补执行迁移，旧表保留")
    void baselinesExistingDatabase() throws SQLException {
        // 模拟老版本部署：库里有表，但没有 Flyway 历史表
        try (Connection connection = connect(DB_BASELINE);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE legacy_marker (id INT PRIMARY KEY)");
        }

        MigrateResult result = flyway(DB_BASELINE).migrate();

        assertEquals(3, result.migrationsExecuted, "baseline 之后仍需执行 V1、V2、V3");
        Set<String> tables = tableNames(DB_BASELINE);
        assertTrue(tables.contains("legacy_marker"), "baseline 不能删除已有表：" + tables);
        assertEquals(EXPECTED_TABLES, remainingTables(tables, "legacy_marker"));
        assertEquals(EXPECTED_SEEDS, rowCounts(DB_BASELINE));
        assertEquals(List.of("0|BASELINE|1", "1|SQL|1", "2|SQL|1", "3|SQL|1"), history(DB_BASELINE));
    }

    // ------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------

    /**
     * 建测试库并授权。建库是全局 DDL，普通用户无权执行，只能用 root；
     * 迁移本身仍用普通用户，与生产一致。
     *
     * <p>MySQLContainer 会把 root 密码设置成与 withPassword 相同的值。</p>
     */
    private static void createDatabase(String database) throws SQLException {
        try (Connection root = DriverManager.getConnection(jdbcUrl(""), "root", MYSQL.getPassword());
             Statement statement = root.createStatement()) {
            statement.execute("CREATE DATABASE IF NOT EXISTS `" + database + "`");
            statement.execute("GRANT ALL PRIVILEGES ON `" + database + "`.* TO '"
                    + MYSQL.getUsername() + "'@'%'");
        }
    }

    /** 与 Nacos 里的 spring.flyway 配置保持一致，配置漂移会在这里先暴露。 */
    private static Flyway flyway(String database) {
        return Flyway.configure()
                .dataSource(jdbcUrl(database), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .table(HISTORY_TABLE)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .cleanDisabled(true)
                .load();
    }

    private static String jdbcUrl(String database) {
        return "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(MySQLContainer.MYSQL_PORT)
                + "/" + database
                + "?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai"
                + "&useSSL=false&allowPublicKeyRetrieval=true";
    }

    private static Connection connect(String database) throws SQLException {
        return DriverManager.getConnection(jdbcUrl(database), MYSQL.getUsername(), MYSQL.getPassword());
    }

    /** 库中除 Flyway 历史表之外的表名。 */
    private static Set<String> tableNames(String database) throws SQLException {
        Set<String> names = new TreeSet<>();
        try (Connection connection = connect(database);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema = ?")) {
            statement.setString(1, database);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String name = resultSet.getString(1);
                    if (!name.startsWith("flyway_schema_history")) {
                        names.add(name);
                    }
                }
            }
        }
        return names;
    }

    private static Map<String, Integer> rowCounts(String database) throws SQLException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String table : new TreeSet<>(EXPECTED_SEEDS.keySet())) {
            counts.put(table, count(database, table));
        }
        return counts;
    }

    private static int count(String database, String table) throws SQLException {
        try (Connection connection = connect(database);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM `" + table + "`")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static String passwordOf(String database, String username) throws SQLException {
        try (Connection connection = connect(database);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT password FROM sys_user WHERE username = ?")) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next(), "用户不存在：" + username);
                return resultSet.getString(1);
            }
        }
    }

    /** 迁移历史的「版本|类型|是否成功」序列，按执行顺序排列。 */
    private static List<String> history(String database) throws SQLException {
        List<String> rows = new ArrayList<>();
        try (Connection connection = connect(database);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT version, type, success FROM `"
                     + HISTORY_TABLE + "` ORDER BY installed_rank")) {
            while (resultSet.next()) {
                rows.add(resultSet.getString("version") + "|" + resultSet.getString("type")
                        + "|" + resultSet.getInt("success"));
            }
        }
        return rows;
    }

    /** 实际表集合去掉测试自己造的表后，应当等于期望集合。 */
    private static Set<String> remainingTables(Set<String> actual, String extraTable) {
        Set<String> remaining = new TreeSet<>(actual);
        remaining.remove(extraTable);
        return remaining;
    }
}
