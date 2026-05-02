package net.isksss.mc.fqdnguard;

import java.util.Set;

/** FQDNGuard の設定値を保持する。 */
record FQDNGuardConfig(
    Set<String> allowedHosts, Set<String> allowedIps, String kickMessage, boolean logRejections) {

  /**
   * 拒否メッセージのプレースホルダーを実際の値へ置換する。
   *
   * @param host プレイヤーが接続に使用したホスト名
   * @return 表示用の拒否メッセージ
   */
  String formatKickMessage(String host) {
    return kickMessage
        .replace("{host}", host)
        .replace("{allowed_hosts}", String.join(", ", allowedHosts));
  }
}
