package net.isksss.mc.fqdnguard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.LinkedHashSet;
import org.junit.jupiter.api.Test;

class FQDNGuardConfigTest {

  @Test
  void formatKickMessageReplacesPlaceholders() {
    FQDNGuardConfig config =
        new FQDNGuardConfig(
            new LinkedHashSet<>(Arrays.asList("mc.example.com", "play.example.com")),
            "Use {allowed_hosts}. You used {host}.",
            true);

    assertEquals(
        "Use mc.example.com, play.example.com. You used direct.example.com.",
        config.formatKickMessage("direct.example.com"));
  }
}
