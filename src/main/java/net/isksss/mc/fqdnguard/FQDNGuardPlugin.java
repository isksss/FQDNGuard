package net.isksss.mc.fqdnguard;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import java.io.IOException;
import java.io.InputStream;
import java.net.IDN;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

@Plugin(
    id = "fqdn-guard",
    name = "FQDNGuard",
    version = "0.1.0",
    authors = {"isksss"},
    description = "Allows players to join only through configured FQDNs.")
public final class FQDNGuardPlugin {
  private static final String CONFIG_FILE_NAME = "fqdn-guard.yml";
  private static final String DEFAULT_CONFIG_RESOURCE = "/" + CONFIG_FILE_NAME;
  private static final String ALLOWED_HOSTS_KEY = "allowed-hosts";
  private static final String KICK_MESSAGE_KEY = "kick-message";
  private static final String LOG_REJECTIONS_KEY = "log-rejections";

  private final Logger logger;
  private final Path dataDirectory;

  private Set<String> allowedHosts = Collections.emptySet();
  private String kickMessage = "";
  private boolean logRejections = false;

  /**
   * FQDNGuard プラグインを生成する。
   *
   * @param logger Velocity が提供するロガー
   * @param dataDirectory プラグイン用データディレクトリ
   */
  @Inject
  public FQDNGuardPlugin(Logger logger, @DataDirectory Path dataDirectory) {
    // Velocity からロガーとプラグイン用データディレクトリを受け取る。
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
    // プロキシ初期化時に設定ファイルを作成・読み込みする。
    loadConfig();
  }

  /**
   * プレイヤーのログイン前に接続先ホスト名を検査し、許可されていない場合は拒否する。
   *
   * @param event プレログインイベント
   */
  @Subscribe
  public void onPreLogin(PreLoginEvent event) {
    // プレイヤーが接続に使用したホスト名を取得し、比較用に正規化する。
    Optional<String> virtualHost =
        event
            .getConnection()
            .getVirtualHost()
            .map(InetSocketAddress::getHostString)
            .map(FQDNGuardPlugin::normalizeHost);

    // 許可済みホスト名で接続している場合はログインを許可する。
    if (virtualHost.isPresent() && allowedHosts.contains(virtualHost.get())) {
      return;
    }

    // 許可されていないホスト名、または取得できない接続はログ出力して拒否する。
    String host = virtualHost.filter(value -> !value.isBlank()).orElse("unknown");
    if (logRejections) {
      logger.info(
          "Rejected login for {} from {} via host '{}'",
          event.getUsername(),
          event.getConnection().getRemoteAddress(),
          host);
    }

    event.setResult(
        PreLoginEvent.PreLoginComponentResult.denied(Component.text(formatKickMessage(host))));
  }

  /** 設定ファイルを用意し、許可ホスト、拒否メッセージ、拒否ログ設定を読み込む。 */
  private void loadConfig() {
    // プラグインのデータディレクトリ配下に設定ファイルを配置する。
    Path configPath = dataDirectory.resolve(CONFIG_FILE_NAME);
    try {
      Config defaultConfig = loadDefaultConfig();
      Files.createDirectories(dataDirectory);
      if (Files.notExists(configPath)) {
        // 初回起動時は resources に同梱した既定の設定ファイルをコピーする。
        copyDefaultConfig(configPath);
      }

      // YAML 形式の設定を読み込む。
      Config config = parseConfig(configPath, defaultConfig);

      // 設定された許可ホストを正規化し、空の場合は安全な既定値へ戻す。
      Set<String> configuredHosts = config.allowedHosts();
      if (configuredHosts.isEmpty()) {
        logger.warn("No allowed hosts configured. Falling back to bundled default config.");
        configuredHosts = defaultConfig.allowedHosts();
      }

      allowedHosts = configuredHosts;
      kickMessage = config.kickMessage();
      logRejections = config.logRejections();

      logger.info("FQDNGuard enabled. Allowed hosts: {}", String.join(", ", allowedHosts));
    } catch (IOException exception) {
      // 設定ファイルの作成・読み込みに失敗した場合も、同梱設定で起動を継続する。
      applyFallbackConfig(exception);
    }
  }

  /**
   * 同梱されているデフォルト設定ファイルを指定パスへコピーする。
   *
   * @param configPath コピー先の設定ファイルパス
   * @throws IOException デフォルト設定の取得またはコピーに失敗した場合
   */
  private static void copyDefaultConfig(Path configPath) throws IOException {
    // JAR 内の resources からデフォルト設定を取り出す。
    try (InputStream inputStream =
        FQDNGuardPlugin.class.getResourceAsStream(DEFAULT_CONFIG_RESOURCE)) {
      if (inputStream == null) {
        throw new IOException("Default config resource not found: " + DEFAULT_CONFIG_RESOURCE);
      }
      Files.copy(inputStream, configPath);
    }
  }

  /**
   * 設定読み込み失敗時に同梱デフォルト設定を適用する。
   *
   * @param exception 設定読み込みで発生した例外
   */
  private void applyFallbackConfig(IOException exception) {
    try {
      Config defaultConfig = loadDefaultConfig();
      allowedHosts = defaultConfig.allowedHosts();
      kickMessage = defaultConfig.kickMessage();
      logRejections = defaultConfig.logRejections();
      logger.error(
          "Failed to load FQDNGuard config. Falling back to bundled default config.", exception);
    } catch (IOException fallbackException) {
      allowedHosts = Collections.emptySet();
      kickMessage = "FQDNGuard configuration failed.";
      logRejections = true;
      logger.error("Failed to load FQDNGuard config.", exception);
      logger.error("Failed to load bundled default config.", fallbackException);
    }
  }

  /**
   * 拒否メッセージのプレースホルダーを実際の値へ置換する。
   *
   * @param host プレイヤーが接続に使用したホスト名
   * @return 表示用の拒否メッセージ
   */
  private String formatKickMessage(String host) {
    // 拒否メッセージ内のプレースホルダーを実際の接続ホストと許可ホスト一覧に置換する。
    return kickMessage
        .replace("{host}", host)
        .replace("{allowed_hosts}", String.join(", ", allowedHosts));
  }

  /**
   * resources に同梱されているデフォルト設定を読み込む。
   *
   * @return デフォルト設定
   * @throws IOException デフォルト設定が見つからない、または読み込めない場合
   */
  private static Config loadDefaultConfig() throws IOException {
    // resources に同梱した YAML をフォールバック用の既定設定として読み込む。
    try (InputStream inputStream =
        FQDNGuardPlugin.class.getResourceAsStream(DEFAULT_CONFIG_RESOURCE)) {
      if (inputStream == null) {
        throw new IOException("Default config resource not found: " + DEFAULT_CONFIG_RESOURCE);
      }
      return parseConfigLines(
          new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).lines().toList(),
          new Config(Collections.emptySet(), "", false));
    }
  }

  /**
   * YAML 設定ファイルを読み込んで設定値へ変換する。
   *
   * @param configPath 読み込む設定ファイルパス
   * @param defaultConfig 未指定項目に使用するデフォルト設定
   * @return 読み込んだ設定
   * @throws IOException 設定ファイルを読み込めない場合
   */
  private static Config parseConfig(Path configPath, Config defaultConfig) throws IOException {
    return parseConfigLines(Files.readAllLines(configPath, StandardCharsets.UTF_8), defaultConfig);
  }

  /**
   * YAML 設定の行リストから、このプラグインで使用する設定値を抽出する。
   *
   * @param lines YAML 設定ファイルの各行
   * @param defaultConfig 未指定項目に使用するデフォルト設定
   * @return 抽出した設定
   */
  private static Config parseConfigLines(List<String> lines, Config defaultConfig) {
    // このプラグインで使うトップレベルキーと allowed-hosts のリストだけを読み込む。
    Set<String> hosts = new LinkedHashSet<>();
    String configuredKickMessage = defaultConfig.kickMessage();
    boolean configuredLogRejections = defaultConfig.logRejections();
    String currentListKey = "";

    for (String line : lines) {
      String value = stripComment(line).trim();
      if (value.isEmpty()) {
        continue;
      }

      if (value.startsWith("-")) {
        if (ALLOWED_HOSTS_KEY.equals(currentListKey)) {
          addAllowedHost(hosts, unquote(value.substring(1).trim()));
        }
        continue;
      }

      int separatorIndex = value.indexOf(':');
      if (separatorIndex < 0) {
        currentListKey = "";
        continue;
      }

      String key = value.substring(0, separatorIndex).trim();
      String rawValue = unquote(value.substring(separatorIndex + 1).trim());
      currentListKey = rawValue.isEmpty() ? key : "";

      if (KICK_MESSAGE_KEY.equals(key) && !rawValue.isEmpty()) {
        configuredKickMessage = rawValue;
      } else if (LOG_REJECTIONS_KEY.equals(key) && !rawValue.isEmpty()) {
        configuredLogRejections = Boolean.parseBoolean(rawValue);
      }
    }

    return new Config(
        Collections.unmodifiableSet(hosts), configuredKickMessage, configuredLogRejections);
  }

  /**
   * 許可ホスト候補を正規化し、空でなければ許可ホスト集合へ追加する。
   *
   * @param hosts 追加先の許可ホスト集合
   * @param host 許可ホスト候補
   */
  private static void addAllowedHost(Set<String> hosts, String host) {
    // YAML から読み込んだホスト名を正規化し、空でなければ許可リストへ追加する。
    String normalizedHost = normalizeHost(host);
    if (!normalizedHost.isBlank()) {
      hosts.add(normalizedHost);
    }
  }

  /**
   * YAML 行からコメント部分を取り除く。
   *
   * @param line コメントを取り除く対象行
   * @return コメントを取り除いた行
   */
  private static String stripComment(String line) {
    // 行頭コメントと、空白の後に続くインラインコメントを除去する。
    boolean quoted = false;
    char quote = '\0';
    for (int index = 0; index < line.length(); index++) {
      char current = line.charAt(index);
      if ((current == '"' || current == '\'') && (index == 0 || line.charAt(index - 1) != '\\')) {
        if (quoted && current == quote) {
          quoted = false;
        } else if (!quoted) {
          quoted = true;
          quote = current;
        }
      }

      if (!quoted
          && current == '#'
          && (index == 0 || Character.isWhitespace(line.charAt(index - 1)))) {
        return line.substring(0, index);
      }
    }
    return line;
  }

  /**
   * 単純な引用符付き文字列から外側の引用符を取り除く。
   *
   * @param value 変換対象の文字列
   * @return 外側の引用符を取り除いた文字列
   */
  private static String unquote(String value) {
    // YAML の単純な引用符付き文字列を設定値として扱いやすい形に戻す。
    if (value.length() < 2) {
      return value;
    }

    char first = value.charAt(0);
    char last = value.charAt(value.length() - 1);
    if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }

  /**
   * ホスト名を比較用に正規化する。
   *
   * @param host 正規化するホスト名
   * @return 小文字化、末尾ドット除去、IDN ASCII 化を行ったホスト名
   */
  private static String normalizeHost(String host) {
    // 大文字小文字や末尾ドットの違いで判定がずれないよう、ホスト名を正規化する。
    String normalized = host.trim().toLowerCase(Locale.ROOT);
    while (normalized.endsWith(".")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }

    if (normalized.isBlank()) {
      return "";
    }

    try {
      // 国際化ドメイン名は ASCII 表記へ変換して比較する。
      return IDN.toASCII(normalized);
    } catch (IllegalArgumentException ignored) {
      return normalized;
    }
  }

  private record Config(Set<String> allowedHosts, String kickMessage, boolean logRejections) {}
}
