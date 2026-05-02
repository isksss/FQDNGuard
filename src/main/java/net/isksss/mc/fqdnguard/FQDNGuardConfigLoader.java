package net.isksss.mc.fqdnguard;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** FQDNGuard の YAML 設定ファイルを読み込む。 */
final class FQDNGuardConfigLoader {
  private static final String CONFIG_FILE_NAME = "fqdn-guard.yml";
  private static final String DEFAULT_CONFIG_RESOURCE = "/" + CONFIG_FILE_NAME;
  private static final String ALLOWED_HOSTS_KEY = "allowed-hosts";
  private static final String ALLOWED_IPS_KEY = "allowed-ips";
  private static final String KICK_MESSAGE_KEY = "kick-message";
  private static final String LOG_REJECTIONS_KEY = "log-rejections";
  private static final FQDNGuardConfig EMPTY_DEFAULT =
      new FQDNGuardConfig(Collections.emptySet(), Collections.emptySet(), "", false);

  private FQDNGuardConfigLoader() {}

  /**
   * 設定ファイルを用意し、設定値を読み込む。
   *
   * @param dataDirectory プラグイン用データディレクトリ
   * @return 読み込んだ設定
   * @throws IOException 設定ファイルの作成または読み込みに失敗した場合
   */
  static FQDNGuardConfig load(Path dataDirectory) throws IOException {
    FQDNGuardConfig defaultConfig = loadDefaultConfig();
    Path configPath = dataDirectory.resolve(CONFIG_FILE_NAME);

    Files.createDirectories(dataDirectory);
    if (Files.notExists(configPath)) {
      copyDefaultConfig(configPath);
    }

    FQDNGuardConfig config = parseConfig(configPath, defaultConfig);
    if (config.allowedHosts().isEmpty()) {
      return new FQDNGuardConfig(
          defaultConfig.allowedHosts(),
          config.allowedIps(),
          config.kickMessage(),
          config.logRejections());
    }
    return config;
  }

  /**
   * 同梱されているデフォルト設定を読み込む。
   *
   * @return デフォルト設定
   * @throws IOException デフォルト設定が見つからない、または読み込めない場合
   */
  static FQDNGuardConfig loadDefaultConfig() throws IOException {
    try (InputStream inputStream =
        FQDNGuardConfigLoader.class.getResourceAsStream(DEFAULT_CONFIG_RESOURCE)) {
      if (inputStream == null) {
        throw new IOException("Default config resource not found: " + DEFAULT_CONFIG_RESOURCE);
      }
      return parseConfigLines(
          new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).lines().toList(),
          EMPTY_DEFAULT);
    }
  }

  /**
   * 同梱されているデフォルト設定ファイルを指定パスへコピーする。
   *
   * @param configPath コピー先の設定ファイルパス
   * @throws IOException デフォルト設定の取得またはコピーに失敗した場合
   */
  static void copyDefaultConfig(Path configPath) throws IOException {
    try (InputStream inputStream =
        FQDNGuardConfigLoader.class.getResourceAsStream(DEFAULT_CONFIG_RESOURCE)) {
      if (inputStream == null) {
        throw new IOException("Default config resource not found: " + DEFAULT_CONFIG_RESOURCE);
      }
      Files.copy(inputStream, configPath);
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
  static FQDNGuardConfig parseConfig(Path configPath, FQDNGuardConfig defaultConfig)
      throws IOException {
    return parseConfigLines(Files.readAllLines(configPath, StandardCharsets.UTF_8), defaultConfig);
  }

  /**
   * YAML 設定の行リストから、このプラグインで使用する設定値を抽出する。
   *
   * @param lines YAML 設定ファイルの各行
   * @param defaultConfig 未指定項目に使用するデフォルト設定
   * @return 抽出した設定
   */
  static FQDNGuardConfig parseConfigLines(List<String> lines, FQDNGuardConfig defaultConfig) {
    Set<String> hosts = new LinkedHashSet<>();
    Set<String> ips = new LinkedHashSet<>();
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
        } else if (ALLOWED_IPS_KEY.equals(currentListKey)) {
          addAllowedIp(ips, unquote(value.substring(1).trim()));
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

      if (ALLOWED_HOSTS_KEY.equals(key) && !rawValue.isEmpty()) {
        addAllowedHost(hosts, rawValue);
      } else if (ALLOWED_IPS_KEY.equals(key) && !rawValue.isEmpty()) {
        addAllowedIp(ips, rawValue);
      } else if (KICK_MESSAGE_KEY.equals(key) && !rawValue.isEmpty()) {
        configuredKickMessage = rawValue;
      } else if (LOG_REJECTIONS_KEY.equals(key) && !rawValue.isEmpty()) {
        configuredLogRejections = Boolean.parseBoolean(rawValue);
      }
    }

    return new FQDNGuardConfig(
        Collections.unmodifiableSet(hosts),
        Collections.unmodifiableSet(ips),
        configuredKickMessage,
        configuredLogRejections);
  }

  /**
   * 許可ホスト候補を正規化し、空でなければ許可ホスト集合へ追加する。
   *
   * @param hosts 追加先の許可ホスト集合
   * @param host 許可ホスト候補
   */
  private static void addAllowedHost(Set<String> hosts, String host) {
    String normalizedHost = HostNormalizer.normalize(host);
    if (!normalizedHost.isBlank()) {
      hosts.add(normalizedHost);
    }
  }

  /**
   * 許可 IP 候補を正規化し、空でなければ許可 IP 集合へ追加する。
   *
   * @param ips 追加先の許可 IP 集合
   * @param ip 許可 IP 候補
   */
  private static void addAllowedIp(Set<String> ips, String ip) {
    String normalizedIp = IpNormalizer.normalize(ip);
    if (!normalizedIp.isBlank()) {
      ips.add(normalizedIp);
    }
  }

  /**
   * YAML 行からコメント部分を取り除く。
   *
   * @param line コメントを取り除く対象行
   * @return コメントを取り除いた行
   */
  static String stripComment(String line) {
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
  static String unquote(String value) {
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
}
