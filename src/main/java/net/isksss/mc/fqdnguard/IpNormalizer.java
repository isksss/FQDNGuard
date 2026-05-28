package net.isksss.mc.fqdnguard;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Locale;

/** 接続元 IP アドレスを比較用に正規化する。 */
final class IpNormalizer {

  private IpNormalizer() {}

  /**
   * ソケットアドレスから IP アドレスを取り出し、比較用に正規化する。
   *
   * @param address 正規化するソケットアドレス
   * @return 比較用に正規化した IP アドレス
   */
  static String normalize(InetSocketAddress address) {
    InetAddress inetAddress = address.getAddress();
    if (inetAddress != null) {
      return normalize(inetAddress.getHostAddress());
    }
    return normalize(address.getHostString());
  }

  /**
   * IP アドレス文字列を比較用に正規化する。
   *
   * @param ip 正規化する IP アドレス
   * @return 比較用に正規化した IP アドレス
   */
  static String normalize(String ip) {
    String normalized = ip.trim().toLowerCase(Locale.ROOT);
    if (normalized.isBlank()) {
      return "";
    }

    if (!isIpLiteral(normalized)) {
      return normalized;
    }

    try {
      return InetAddress.getByName(normalized).getHostAddress().toLowerCase(Locale.ROOT);
    } catch (UnknownHostException ignored) {
      return normalized;
    }
  }

  /**
   * 文字列が IP リテラルとして扱える形式かを判定する。
   *
   * @param value 判定する文字列
   * @return IPv4 または IPv6 リテラルらしい形式の場合は true
   */
  static boolean isIpLiteral(String value) {
    if (value.contains(":")) {
      return true;
    }
    return value.chars().allMatch(character -> Character.isDigit(character) || character == '.');
  }
}
