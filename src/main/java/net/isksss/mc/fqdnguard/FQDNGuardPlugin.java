package net.isksss.mc.fqdnguard;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

@Plugin(
    id = "fqdn-guard",
    name = "FQDNGuard",
    version = "0.1.0",
    authors = {"isksss"},
    description = "Allows players to join only through configured FQDNs.")
public final class FQDNGuardPlugin {
  private final Logger logger;
  private final Path dataDirectory;

  private FQDNGuardConfig config =
      new FQDNGuardConfig(Collections.emptySet(), Collections.emptySet(), "", false);

  /**
   * FQDNGuard プラグインを生成する。
   *
   * @param logger Velocity が提供するロガー
   * @param dataDirectory プラグイン用データディレクトリ
   */
  @Inject
  public FQDNGuardPlugin(Logger logger, @DataDirectory Path dataDirectory) {
    this.logger = logger;
    this.dataDirectory = dataDirectory;
  }

  /**
   * プロキシ初期化時に設定を読み込む。
   *
   * @param event プロキシ初期化イベント
   */
  @Subscribe
  public void onProxyInitialize(ProxyInitializeEvent event) {
    loadConfig();
  }

  /**
   * プレイヤーのログイン前に接続先ホスト名を検査し、許可されていない場合は拒否する。
   *
   * @param event プレログインイベント
   */
  @Subscribe
  public void onPreLogin(PreLoginEvent event) {
    Optional<String> virtualHost =
        event
            .getConnection()
            .getVirtualHost()
            .map(InetSocketAddress::getHostString)
            .map(HostNormalizer::normalize);

    if (virtualHost.isPresent() && config.allowedHosts().contains(virtualHost.get())) {
      return;
    }

    String remoteIp = IpNormalizer.normalize(event.getConnection().getRemoteAddress());
    if (config.allowedIps().contains(remoteIp)) {
      return;
    }

    String host = virtualHost.filter(value -> !value.isBlank()).orElse("unknown");
    if (config.logRejections()) {
      logger.info(
          "Rejected login for {} from {} via host '{}'",
          event.getUsername(),
          event.getConnection().getRemoteAddress(),
          host);
    }

    event.setResult(
        PreLoginEvent.PreLoginComponentResult.denied(
            Component.text(config.formatKickMessage(host))));
  }

  /** 設定ファイルを用意し、許可ホスト、許可 IP、拒否メッセージ、拒否ログ設定を読み込む。 */
  private void loadConfig() {
    try {
      config = FQDNGuardConfigLoader.load(dataDirectory);
      logger.info(
          "FQDNGuard enabled. Allowed hosts: {}. Allowed IPs: {}",
          String.join(", ", config.allowedHosts()),
          String.join(", ", config.allowedIps()));
    } catch (IOException exception) {
      applyFallbackConfig(exception);
    }
  }

  /**
   * 設定読み込み失敗時に同梱デフォルト設定を適用する。
   *
   * @param exception 設定読み込みで発生した例外
   */
  private void applyFallbackConfig(IOException exception) {
    try {
      config = FQDNGuardConfigLoader.loadDefaultConfig();
      logger.error(
          "Failed to load FQDNGuard config. Falling back to bundled default config.", exception);
    } catch (IOException fallbackException) {
      config =
          new FQDNGuardConfig(
              Collections.emptySet(),
              Collections.emptySet(),
              "FQDNGuard configuration failed.",
              true);
      logger.error("Failed to load FQDNGuard config.", exception);
      logger.error("Failed to load bundled default config.", fallbackException);
    }
  }
}
