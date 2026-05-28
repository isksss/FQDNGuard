package net.isksss.mc.fqdnguard;

/** ログイン拒否の理由を表す。 */
enum RejectionReason {
  DIRECT_IP("direct-ip"),
  MISSING_HOST("missing-host"),
  DISALLOWED_HOST("disallowed-host");

  private final String configValue;

  RejectionReason(String configValue) {
    this.configValue = configValue;
  }

  /**
   * 設定やログに出力する拒否理由名を返す。
   *
   * @return 拒否理由名
   */
  String configValue() {
    return configValue;
  }
}
