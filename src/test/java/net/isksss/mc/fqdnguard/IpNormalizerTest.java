package net.isksss.mc.fqdnguard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.InetSocketAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link IpNormalizer} の IP アドレス正規化を検証するテスト。 */
@DisplayName("IpNormalizer の正規化テスト")
class IpNormalizerTest {

  /**
   * IPv4 アドレスが比較用に正規化されることを確認する。
   *
   * <p>期待結果: 前後の空白が取り除かれた IPv4 アドレスが得られる。
   */
  @Test
  @DisplayName("IPv4 アドレスを正規化する")
  void normalizeIpv4Address() {
    assertEquals(
        "127.0.0.1", IpNormalizer.normalize(" 127.0.0.1 "), "IPv4 アドレスは前後の空白を取り除いて比較する必要があります。");
  }

  /**
   * IPv6 アドレスが比較用に正規化されることを確認する。
   *
   * <p>期待結果: 短縮表記の IPv6 アドレスが {@link java.net.InetAddress#getHostAddress()} と同じ形式に正規化される。
   */
  @Test
  @DisplayName("IPv6 アドレスを正規化する")
  void normalizeIpv6Address() {
    assertEquals(
        "0:0:0:0:0:0:0:1",
        IpNormalizer.normalize("::1"),
        "IPv6 アドレスは接続元アドレスと比較できる形式に正規化する必要があります。");
  }

  /**
   * ソケットアドレスから IP アドレスが取り出されることを確認する。
   *
   * <p>期待結果: {@link InetSocketAddress} の IP アドレスだけが比較用文字列として得られる。
   */
  @Test
  @DisplayName("ソケットアドレスから IP アドレスを正規化する")
  void normalizeSocketAddress() {
    assertEquals(
        "127.0.0.1",
        IpNormalizer.normalize(new InetSocketAddress("127.0.0.1", 25565)),
        "ソケットアドレスからはポートを除いた IP アドレスを使う必要があります。");
  }
}
