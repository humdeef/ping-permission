package com.ping.user;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模块边界测试：不依赖额外插件，离线也能执行。
 *
 * <p>pc 与 app 互不依赖；common/auth 不依赖具体领域模块；
 * 源码中也不允许出现跨域 import。</p>
 */
class ModuleBoundaryTest {

    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void pcMustNotDependOnApp() throws IOException {
        String pom = Files.readString(ROOT.resolve("user-pc/pom.xml"));
        assertFalse(pom.contains("<artifactId>user-app</artifactId>"),
                "user-pc 不得依赖 user-app");
    }

    @Test
    void appMustNotDependOnPc() throws IOException {
        String pom = Files.readString(ROOT.resolve("user-app/pom.xml"));
        assertFalse(pom.contains("<artifactId>user-pc</artifactId>"),
                "user-app 不得依赖 user-pc");
    }

    @Test
    void pcSourcesMustNotImportApp() throws IOException {
        assertNoCrossImport("user-pc/src/main/java", "com.ping.user.app");
    }

    @Test
    void appSourcesMustNotImportPc() throws IOException {
        assertNoCrossImport("user-app/src/main/java", "com.ping.user.pc");
    }

    @Test
    void commonAndAuthMustNotImportDomains() throws IOException {
        assertNoCrossImport("user-common/src/main/java", "com.ping.user.pc");
        assertNoCrossImport("user-common/src/main/java", "com.ping.user.app");
        assertNoCrossImport("user-auth/src/main/java", "com.ping.user.pc");
        assertNoCrossImport("user-auth/src/main/java", "com.ping.user.app");
    }

    private void assertNoCrossImport(String relativeDir, String forbidden) throws IOException {
        Path dir = ROOT.resolve(relativeDir);
        assertTrue(Files.isDirectory(dir), "目录不存在: " + dir);
        try (Stream<Path> files = Files.walk(dir)) {
            List<Path> offenders = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> contains(p, forbidden))
                    .toList();
            assertTrue(offenders.isEmpty(), "发现跨域引用 " + forbidden + ": " + offenders);
        }
    }

    private boolean contains(Path file, String text) {
        try {
            return Files.readString(file).contains(text);
        } catch (IOException e) {
            return false;
        }
    }
}
