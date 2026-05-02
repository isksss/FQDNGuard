package net.isksss.mc.fqdnguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FQDNGuardConfigLoaderTest {

  @TempDir Path temporaryDirectory;

  @Test
  void loadCreatesDefaultConfigFileWhenMissing() throws IOException {
    FQDNGuardConfig config = FQDNGuardConfigLoader.load(temporaryDirectory);

    assertEquals(Set.of("mc.example.com"), config.allowedHosts());
    assertTrue(Files.exists(temporaryDirectory.resolve("fqdn-guard.yml")));
  }

  @Test
  void loadParsesYamlListAndNormalizesHosts() throws IOException {
    Path configPath = temporaryDirectory.resolve("fqdn-guard.yml");
    Files.writeString(
        configPath,
        """
        allowed-hosts:
          - MC.Example.COM.
          - play.example.com # inline comment
          - "例え.example"

        kick-message: "Use {allowed_hosts}, not {host}."
        log-rejections: false
        """,
        StandardCharsets.UTF_8);

    FQDNGuardConfig config = FQDNGuardConfigLoader.load(temporaryDirectory);

    assertEquals(
        Set.of("mc.example.com", "play.example.com", "xn--r8jz45g.example"), config.allowedHosts());
    assertEquals("Use {allowed_hosts}, not {host}.", config.kickMessage());
    assertEquals(false, config.logRejections());
  }

  @Test
  void parseConfigLinesUsesDefaultValuesForMissingOptionalKeys() {
    FQDNGuardConfig defaultConfig =
        new FQDNGuardConfig(Set.of("default.example.com"), "Default {host}", true);

    FQDNGuardConfig config =
        FQDNGuardConfigLoader.parseConfigLines(
            List.of("allowed-hosts:", "  - mc.example.com"), defaultConfig);

    assertEquals(Set.of("mc.example.com"), config.allowedHosts());
    assertEquals("Default {host}", config.kickMessage());
    assertTrue(config.logRejections());
  }

  @Test
  void stripCommentKeepsHashInsideQuotedValue() {
    assertEquals(
        "kick-message: \"Use #1 server\" ",
        FQDNGuardConfigLoader.stripComment("kick-message: \"Use #1 server\" # comment"));
  }

  @Test
  void unquoteRemovesMatchingOuterQuotes() {
    assertEquals("mc.example.com", FQDNGuardConfigLoader.unquote("\"mc.example.com\""));
    assertEquals("play.example.com", FQDNGuardConfigLoader.unquote("'play.example.com'"));
  }
}
