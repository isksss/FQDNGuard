package net.isksss.mc.fqdnguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link FQDNGuardConfigLoader} の設定ファイル生成と YAML 解析を検証するテスト。 */
@DisplayName("FQDNGuardConfigLoader の設定読み込みテスト")
class FQDNGuardConfigLoaderTest {

  @TempDir Path temporaryDirectory;

  /**
   * 設定ファイルが存在しない場合に、resources のデフォルト設定がコピーされることを確認する。
   *
   * <p>期待結果: {@code fqdn-guard.yml} が生成され、デフォルトの許可ホストとして {@code mc.example.com} が読み込まれる。
   */
  @Test
  @DisplayName("設定ファイルがない場合はデフォルト設定を生成する")
  void loadCreatesDefaultConfigFileWhenMissing() throws IOException {
    FQDNGuardConfig config = FQDNGuardConfigLoader.load(temporaryDirectory);

    assertEquals(
        Set.of("mc.example.com"),
        config.allowedHosts(),
        "デフォルト設定から mc.example.com が許可ホストとして読み込まれる必要があります。");
    assertTrue(
        Files.exists(temporaryDirectory.resolve("fqdn-guard.yml")),
        "設定ファイルが存在しない場合は fqdn-guard.yml が生成される必要があります。");
  }

  /**
   * YAML のリスト形式で書かれた許可ホストが読み込まれ、比較用に正規化されることを確認する。
   *
   * <p>期待結果: 大文字、末尾ドット、インラインコメント、引用符付き国際化ドメイン名を含む設定から、正規化済みの許可ホスト一覧が得られる。
   */
  @Test
  @DisplayName("YAML の許可ホストリストを読み込み正規化する")
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
        Set.of("mc.example.com", "play.example.com", "xn--r8jz45g.example"),
        config.allowedHosts(),
        "YAML の allowed-hosts は正規化されたホスト集合として読み込まれる必要があります。");
    assertEquals(
        "Use {allowed_hosts}, not {host}.",
        config.kickMessage(),
        "kick-message は YAML の値をそのまま読み込む必要があります。");
    assertEquals(false, config.logRejections(), "log-rejections: false は false として読み込む必要があります。");
  }

  /**
   * YAML に任意項目がない場合、デフォルト設定の値が使われることを確認する。
   *
   * <p>期待結果: {@code kick-message} と {@code log-rejections} が未指定の場合、渡したデフォルト設定の値が保持される。
   */
  @Test
  @DisplayName("未指定の任意項目はデフォルト設定を使う")
  void parseConfigLinesUsesDefaultValuesForMissingOptionalKeys() {
    FQDNGuardConfig defaultConfig =
        new FQDNGuardConfig(Set.of("default.example.com"), Set.of(), "Default {host}", true);

    FQDNGuardConfig config =
        FQDNGuardConfigLoader.parseConfigLines(
            List.of("allowed-hosts:", "  - mc.example.com"), defaultConfig);

    assertEquals(
        Set.of("mc.example.com"), config.allowedHosts(), "allowed-hosts は YAML の値を優先する必要があります。");
    assertEquals(
        "Default {host}", config.kickMessage(), "kick-message が未指定の場合はデフォルト設定の値を使う必要があります。");
    assertTrue(config.logRejections(), "log-rejections が未指定の場合はデフォルト設定の値を使う必要があります。");
  }

  /**
   * YAML のリスト形式で書かれた許可 IP が読み込まれ、比較用に正規化されることを確認する。
   *
   * <p>期待結果: {@code allowed-ips} から IPv4 と IPv6 の許可 IP 一覧が得られる。
   */
  @Test
  @DisplayName("YAML の許可 IP リストを読み込み正規化する")
  void loadParsesYamlListAndNormalizesIps() throws IOException {
    Path configPath = temporaryDirectory.resolve("fqdn-guard.yml");
    Files.writeString(
        configPath,
        """
        allowed-hosts:
          - mc.example.com
        allowed-ips:
          - 127.0.0.1
          - "::1"
        """,
        StandardCharsets.UTF_8);

    FQDNGuardConfig config = FQDNGuardConfigLoader.load(temporaryDirectory);

    assertEquals(
        Set.of("127.0.0.1", "0:0:0:0:0:0:0:1"),
        config.allowedIps(),
        "YAML の allowed-ips は正規化された IP 集合として読み込まれる必要があります。");
  }

  /**
   * 引用符内の {@code #} はコメントとして扱わず、引用符外のコメントだけを除去することを確認する。
   *
   * <p>期待結果: {@code "Use #1 server"} の {@code #} は残り、末尾の {@code # comment} だけが除去される。
   */
  @Test
  @DisplayName("引用符内の # はコメントとして扱わない")
  void stripCommentKeepsHashInsideQuotedValue() {
    assertEquals(
        "kick-message: \"Use #1 server\" ",
        FQDNGuardConfigLoader.stripComment("kick-message: \"Use #1 server\" # comment"),
        "引用符内の # は値の一部として残し、引用符外のコメントだけを除去する必要があります。");
  }

  /**
   * 単純な引用符付き文字列から、外側の引用符だけを取り除くことを確認する。
   *
   * <p>期待結果: ダブルクォートとシングルクォートのどちらでも、外側の引用符が取り除かれる。
   */
  @Test
  @DisplayName("外側の引用符を取り除く")
  void unquoteRemovesMatchingOuterQuotes() {
    assertEquals(
        "mc.example.com",
        FQDNGuardConfigLoader.unquote("\"mc.example.com\""),
        "ダブルクォートで囲まれた値は外側の引用符を取り除く必要があります。");
    assertEquals(
        "play.example.com",
        FQDNGuardConfigLoader.unquote("'play.example.com'"),
        "シングルクォートで囲まれた値は外側の引用符を取り除く必要があります。");
  }
}
