package net.isksss.mc.fqdnguard;

import java.util.Set;

/** FQDNGuard の設定値を保持する。 */
record FQDNGuardConfig(
    Set<String> allowedHosts,
    Set<String> allowedWildcardHosts,
    Set<String> allowedIps,
    Set<CidrRange> allowedIpRanges,
    String kickMessage,
    String directIpKickMessage,
    String missingHostKickMessage,
    String disallowedHostKickMessage,
    boolean logRejections,
    boolean logAllowedIpBypasses,
    Set<String> validationWarnings) {

  /**
   * ホスト名が許可対象か判定する。
   *
   * @param host 判定するホスト名
   * @return 許可対象の場合は true
   */
  boolean allowsHost(String host) {
    String normalizedHost = HostNormalizer.normalize(host);
    if (allowedHosts.contains(normalizedHost)) {
      return true;
    }
    return allowedWildcardHosts.stream()
        .anyMatch(
            wildcardHost ->
                normalizedHost.length() > wildcardHost.length()
                    && normalizedHost.endsWith("." + wildcardHost));
  }

  /**
   * IP アドレスが許可対象か判定する。
   *
   * @param ip 判定する IP アドレス
   * @return 許可対象の場合は true
   */
  boolean allowsIp(String ip) {
    String normalizedIp = IpNormalizer.normalize(ip);
    if (allowedIps.contains(normalizedIp)) {
      return true;
    }
    return allowedIpRanges.stream().anyMatch(range -> range.contains(normalizedIp));
  }

  /**
   * 拒否メッセージのプレースホルダーを実際の値へ置換する。
   *
   * @param reason 拒否理由
   * @param host プレイヤーが接続に使用したホスト名
   * @param remoteIp 接続元 IP アドレス
   * @return 表示用の拒否メッセージ
   */
  String formatKickMessage(RejectionReason reason, String host, String remoteIp) {
    String message =
        switch (reason) {
          case DIRECT_IP -> directIpKickMessage;
          case MISSING_HOST -> missingHostKickMessage;
          case DISALLOWED_HOST -> disallowedHostKickMessage;
        };
    if (message.isBlank()) {
      message = kickMessage;
    }
    return message
        .replace("{host}", host)
        .replace("{allowed_hosts}", allowedHostsText())
        .replace("{remote_ip}", remoteIp)
        .replace("{reason}", reason.configValue());
  }

  /**
   * 設定済み許可ホストを表示用文字列へ変換する。
   *
   * @return 表示用の許可ホスト一覧
   */
  String allowedHostsText() {
    String exactHosts = String.join(", ", allowedHosts);
    String wildcardHosts =
        String.join(", ", allowedWildcardHosts.stream().map(host -> "*." + host).toList());
    if (exactHosts.isBlank()) {
      return wildcardHosts;
    }
    if (wildcardHosts.isBlank()) {
      return exactHosts;
    }
    return exactHosts + ", " + wildcardHosts;
  }
}
