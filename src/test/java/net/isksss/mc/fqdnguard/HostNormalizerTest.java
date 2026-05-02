package net.isksss.mc.fqdnguard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link HostNormalizer} のホスト名正規化処理を検証するテスト。 */
@DisplayName("HostNormalizer の正規化テスト")
class HostNormalizerTest {

  /**
   * 大文字小文字と末尾ドットを含むホスト名が、比較用の標準形式へ変換されることを確認する。
   *
   * <p>期待結果: 前後空白が削除され、小文字化され、末尾ドットが除去された {@code mc.example.com} になる。
   */
  @Test
  @DisplayName("大文字小文字と末尾ドットを正規化する")
  void normalizeLowercasesAndRemovesTrailingDots() {
    assertEquals(
        "mc.example.com",
        HostNormalizer.normalize(" MC.Example.COM.. "),
        "ホスト名は小文字化され、末尾ドットが除去される必要があります。");
  }

  /**
   * 空白だけのホスト名が、許可ホストとして扱われない空文字へ変換されることを確認する。
   *
   * <p>期待結果: 空白だけの入力は {@code ""} になる。
   */
  @Test
  @DisplayName("空白だけのホスト名は空文字に正規化する")
  void normalizeReturnsEmptyStringForBlankHost() {
    assertEquals("", HostNormalizer.normalize("   "), "空白だけのホスト名は空文字に正規化される必要があります。");
  }

  /**
   * 日本語を含む国際化ドメイン名が、比較可能な ASCII 表記へ変換されることを確認する。
   *
   * <p>期待結果: {@code 例え.example} は punycode の {@code xn--r8jz45g.example} になる。
   */
  @Test
  @DisplayName("国際化ドメイン名を ASCII 表記へ変換する")
  void normalizeConvertsInternationalDomainNameToAscii() {
    assertEquals(
        "xn--r8jz45g.example",
        HostNormalizer.normalize("例え.example"),
        "国際化ドメイン名は IDN ASCII 表記へ変換される必要があります。");
  }
}
