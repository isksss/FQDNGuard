package net.isksss.mc.fqdnguard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.LinkedHashSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link FQDNGuardConfig} の設定値操作を検証するテスト。 */
@DisplayName("FQDNGuardConfig の設定値操作テスト")
class FQDNGuardConfigTest {

  /**
   * キックメッセージ内のプレースホルダーが、接続ホスト名と許可ホスト一覧に置換されることを確認する。
   *
   * <p>期待結果: {@code {allowed_hosts}} は許可ホスト一覧、{@code {host}} は実際の接続ホストに置換される。
   */
  @Test
  @DisplayName("キックメッセージのプレースホルダーを置換する")
  void formatKickMessageReplacesPlaceholders() {
    FQDNGuardConfig config =
        new FQDNGuardConfig(
            new LinkedHashSet<>(Arrays.asList("mc.example.com", "play.example.com")),
            new LinkedHashSet<>(),
            "Use {allowed_hosts}. You used {host}.",
            true);

    assertEquals(
        "Use mc.example.com, play.example.com. You used direct.example.com.",
        config.formatKickMessage("direct.example.com"),
        "キックメッセージ内の {allowed_hosts} と {host} は実際の値へ置換される必要があります。");
  }
}
