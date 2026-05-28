package net.isksss.mc.fqdnguard;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
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
  private static final String COMMAND_PERMISSION = "fqdnguard.command";

  private final ProxyServer proxyServer;
  private final Logger logger;
  private final Path dataDirectory;

  private FQDNGuardConfig config =
      new FQDNGuardConfig(
          Collections.emptySet(),
          Collections.emptySet(),
          Collections.emptySet(),
          Collections.emptySet(),
          "",
          "",
          "",
          "",
          false,
          false,
          Collections.emptySet());

  /**
   * FQDNGuard プラグインを生成する。
   *
   * @param proxyServer Velocity が提供するプロキシサーバー
   * @param logger Velocity が提供するロガー
   * @param dataDirectory プラグイン用データディレクトリ
   */
  @Inject
  public FQDNGuardPlugin(
      ProxyServer proxyServer, Logger logger, @DataDirectory Path dataDirectory) {
    this.proxyServer = proxyServer;
    this.logger = logger;
    this.dataDirectory = dataDirectory;
  }

  /**
   * プロキシ初期化時に設定を読み込み、管理コマンドを登録する。
   *
   * @param event プロキシ初期化イベント
   */
  @Subscribe
  public void onProxyInitialize(ProxyInitializeEvent event) {
    loadConfig();
    registerCommand();
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
    String remoteIp = IpNormalizer.normalize(event.getConnection().getRemoteAddress());

    if (virtualHost.isPresent() && config.allowsHost(virtualHost.get())) {
      return;
    }

    if (config.allowsIp(remoteIp)) {
      if (config.logAllowedIpBypasses()) {
        logger.info(
            "Allowed login for {} from {} via IP bypass. host='{}'",
            event.getUsername(),
            remoteIp,
            virtualHost.orElse("unknown"));
      }
      return;
    }

    String host = virtualHost.filter(value -> !value.isBlank()).orElse("unknown");
    RejectionReason reason = rejectionReason(virtualHost);
    if (config.logRejections()) {
      logger.info(
          "Rejected login for {} from {} via host '{}' reason={}",
          event.getUsername(),
          remoteIp,
          host,
          reason.configValue());
    }

    event.setResult(
        PreLoginEvent.PreLoginComponentResult.denied(
            Component.text(config.formatKickMessage(reason, host, remoteIp))));
  }

  private RejectionReason rejectionReason(Optional<String> virtualHost) {
    if (virtualHost.isEmpty() || virtualHost.get().isBlank()) {
      return RejectionReason.MISSING_HOST;
    }
    if (HostNormalizer.isIpLiteral(virtualHost.get())) {
      return RejectionReason.DIRECT_IP;
    }
    return RejectionReason.DISALLOWED_HOST;
  }

  private void registerCommand() {
    CommandManager commandManager = proxyServer.getCommandManager();
    CommandMeta commandMeta =
        commandManager.metaBuilder("fqdnguard").aliases("fqg").plugin(this).build();
    commandManager.register(commandMeta, new FQDNGuardCommand());
  }

  /** 設定ファイルを用意し、許可ホスト、許可 IP、拒否メッセージ、ログ設定を読み込む。 */
  private boolean loadConfig() {
    try {
      config = FQDNGuardConfigLoader.load(dataDirectory);
      for (String warning : config.validationWarnings()) {
        logger.warn("FQDNGuard config warning: {}", warning);
      }
      logger.info(
          "FQDNGuard enabled. Allowed hosts: {}. Allowed IPs: {}. Allowed IP ranges: {}",
          config.allowedHostsText(),
          String.join(", ", config.allowedIps()),
          String.join(", ", config.allowedIpRanges().stream().map(CidrRange::value).toList()));
      return true;
    } catch (IOException exception) {
      applyFallbackConfig(exception);
      return false;
    }
  }

  private String statusText() {
    return "FQDNGuard status: exact-hosts="
        + config.allowedHosts().size()
        + ", wildcard-hosts="
        + config.allowedWildcardHosts().size()
        + ", allowed-ips="
        + config.allowedIps().size()
        + ", allowed-ip-ranges="
        + config.allowedIpRanges().size();
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
              Collections.emptySet(),
              Collections.emptySet(),
              "FQDNGuard configuration failed.",
              "FQDNGuard configuration failed.",
              "FQDNGuard configuration failed.",
              "FQDNGuard configuration failed.",
              true,
              false,
              Collections.emptySet());
      logger.error("Failed to load FQDNGuard config.", exception);
      logger.error("Failed to load bundled default config.", fallbackException);
    }
  }

  /** FQDNGuard の管理コマンドを処理する。 */
  private final class FQDNGuardCommand implements SimpleCommand {

    @Override
    public void execute(Invocation invocation) {
      String[] arguments = invocation.arguments();
      if (arguments.length == 0 || "status".equalsIgnoreCase(arguments[0])) {
        invocation.source().sendPlainMessage(statusText());
        return;
      }

      if ("reload".equalsIgnoreCase(arguments[0])) {
        if (loadConfig()) {
          invocation.source().sendPlainMessage("FQDNGuard config reloaded.");
        } else {
          invocation
              .source()
              .sendPlainMessage("FQDNGuard config reload failed. Check console logs.");
        }
        return;
      }

      invocation.source().sendPlainMessage("Usage: /fqdnguard <status|reload>");
    }

    @Override
    public List<String> suggest(Invocation invocation) {
      String[] arguments = invocation.arguments();
      if (arguments.length <= 1) {
        return List.of("status", "reload");
      }
      return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
      return invocation.source().hasPermission(COMMAND_PERMISSION);
    }
  }
}
