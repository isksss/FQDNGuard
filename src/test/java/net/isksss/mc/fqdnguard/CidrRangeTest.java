package net.isksss.mc.fqdnguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link CidrRange} の CIDR 解析と一致判定を検証するテスト。 */
@DisplayName("CidrRange の CIDR 判定テスト")
class CidrRangeTest {

  /**
   * IPv4 CIDR の範囲内外を判定できることを確認する。
   *
   * <p>期待結果: 同じ /24 の IP は一致し、別ネットワークの IP は一致しない。
   */
  @Test
  @DisplayName("IPv4 CIDR の範囲内外を判定する")
  void containsMatchesIpv4Range() {
    CidrRange range = CidrRange.parse("192.168.0.0/24").orElseThrow();

    assertEquals(true, range.contains("192.168.0.10"), "同じ /24 の IP は許可される必要があります。");
    assertEquals(false, range.contains("192.168.1.10"), "別ネットワークの IP は許可されない必要があります。");
  }

  /**
   * IPv6 CIDR の範囲内外を判定できることを確認する。
   *
   * <p>期待結果: 同じ /32 の IP は一致し、別ネットワークの IP は一致しない。
   */
  @Test
  @DisplayName("IPv6 CIDR の範囲内外を判定する")
  void containsMatchesIpv6Range() {
    CidrRange range = CidrRange.parse("2001:db8::/32").orElseThrow();

    assertEquals(true, range.contains("2001:db8::1"), "同じ /32 の IP は許可される必要があります。");
    assertEquals(false, range.contains("2001:db9::1"), "別ネットワークの IP は許可されない必要があります。");
  }

  /**
   * 不正な CIDR が解析失敗として扱われることを確認する。
   *
   * <p>期待結果: プレフィックス長がアドレス種別の範囲外の場合は空になる。
   */
  @Test
  @DisplayName("不正な CIDR を拒否する")
  void parseRejectsInvalidCidr() {
    assertTrue(CidrRange.parse("192.168.0.0/33").isEmpty(), "IPv4 の /33 は拒否される必要があります。");
  }
}
