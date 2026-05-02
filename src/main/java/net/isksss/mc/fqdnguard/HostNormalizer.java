package net.isksss.mc.fqdnguard;

import java.net.IDN;
import java.util.Locale;

/** 接続ホスト名を比較用に正規化する。 */
final class HostNormalizer {

  private HostNormalizer() {}

  /**
   * ホスト名を比較用に正規化する。
   *
   * @param host 正規化するホスト名
   * @return 小文字化、末尾ドット除去、IDN ASCII 化を行ったホスト名
   */
  static String normalize(String host) {
    String normalized = host.trim().toLowerCase(Locale.ROOT);
    while (normalized.endsWith(".")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }

    if (normalized.isBlank()) {
      return "";
    }

    try {
      return IDN.toASCII(normalized);
    } catch (IllegalArgumentException ignored) {
      return normalized;
    }
  }
}
