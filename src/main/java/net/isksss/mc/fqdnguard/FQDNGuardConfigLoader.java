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
  private static final String ALLOWED_IP_RANGES_KEY = "allowed-ip-ranges";
  private static final String KICK_MESSAGE_KEY = "kick-message";
  private static final String DIRECT_IP_KICK_MESSAGE_KEY = "kick-message-direct-ip";
  private static final String MISSING_HOST_KICK_MESSAGE_KEY = "kick-message-missing-host";
  private static final String DISALLOWED_HOST_KICK_MESSAGE_KEY = "kick-message-disallowed-host";
  private static final String LOG_REJECTIONS_KEY = "log-rejections";
  private static final String LOG_ALLOWED_IP_BYPASSES_KEY = "log-allowed-ip-bypasses";
  private static final FQDNGuardConfig EMPTY_DEFAULT =
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
    if (config.allowedHosts().isEmpty() && config.allowedWildcardHosts().isEmpty()) {
      return new FQDNGuardConfig(
          defaultConfig.allowedHosts(),
          defaultConfig.allowedWildcardHosts(),
          config.allowedIps(),
          config.allowedIpRanges(),
          config.kickMessage(),
          config.directIpKickMessage(),
          config.missingHostKickMessage(),
          config.disallowedHostKickMessage(),
          config.logRejections(),
          config.logAllowedIpBypasses(),
          config.validationWarnings());
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
    Set<String> wildcardHosts = new LinkedHashSet<>();
    Set<String> ips = new LinkedHashSet<>();
    Set<CidrRange> ipRanges = new LinkedHashSet<>();
    Set<String> warnings = new LinkedHashSet<>();
    String configuredKickMessage = defaultConfig.kickMessage();
    String configuredDirectIpKickMessage = defaultConfig.directIpKickMessage();
    String configuredMissingHostKickMessage = defaultConfig.missingHostKickMessage();
    String configuredDisallowedHostKickMessage = defaultConfig.disallowedHostKickMessage();
    boolean configuredLogRejections = defaultConfig.logRejections();
    boolean configuredLogAllowedIpBypasses = defaultConfig.logAllowedIpBypasses();
    String currentListKey = "";

    for (String line : lines) {
      String value = stripComment(line).trim();
      if (value.isEmpty()) {
        continue;
      }

      if (value.startsWith("-")) {
        addListValue(
            currentListKey,
            unquote(value.substring(1).trim()),
            hosts,
            wildcardHosts,
            ips,
            ipRanges,
            warnings);
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

      if (!rawValue.isEmpty()) {
        addListValue(key, rawValue, hosts, wildcardHosts, ips, ipRanges, warnings);
      }
      if (KICK_MESSAGE_KEY.equals(key) && !rawValue.isEmpty()) {
        configuredKickMessage = rawValue;
      } else if (DIRECT_IP_KICK_MESSAGE_KEY.equals(key) && !rawValue.isEmpty()) {
        configuredDirectIpKickMessage = rawValue;
      } else if (MISSING_HOST_KICK_MESSAGE_KEY.equals(key) && !rawValue.isEmpty()) {
        configuredMissingHostKickMessage = rawValue;
      } else if (DISALLOWED_HOST_KICK_MESSAGE_KEY.equals(key) && !rawValue.isEmpty()) {
        configuredDisallowedHostKickMessage = rawValue;
      } else if (LOG_REJECTIONS_KEY.equals(key) && !rawValue.isEmpty()) {
        configuredLogRejections = Boolean.parseBoolean(rawValue);
      } else if (LOG_ALLOWED_IP_BYPASSES_KEY.equals(key) && !rawValue.isEmpty()) {
        configuredLogAllowedIpBypasses = Boolean.parseBoolean(rawValue);
      }
    }

    return new FQDNGuardConfig(
        Collections.unmodifiableSet(hosts),
        Collections.unmodifiableSet(wildcardHosts),
        Collections.unmodifiableSet(ips),
        Collections.unmodifiableSet(ipRanges),
        configuredKickMessage,
        configuredDirectIpKickMessage,
        configuredMissingHostKickMessage,
        configuredDisallowedHostKickMessage,
        configuredLogRejections,
        configuredLogAllowedIpBypasses,
        Collections.unmodifiableSet(warnings));
  }

  private static void addListValue(
      String key,
      String value,
      Set<String> hosts,
      Set<String> wildcardHosts,
      Set<String> ips,
      Set<CidrRange> ipRanges,
      Set<String> warnings) {
    if (ALLOWED_HOSTS_KEY.equals(key)) {
      addAllowedHost(hosts, wildcardHosts, warnings, value);
    } else if (ALLOWED_IPS_KEY.equals(key)) {
      addAllowedIp(ips, value);
    } else if (ALLOWED_IP_RANGES_KEY.equals(key)) {
      addAllowedIpRange(ipRanges, warnings, value);
    }
  }

  private static void addAllowedHost(
      Set<String> hosts, Set<String> wildcardHosts, Set<String> warnings, String host) {
    String normalizedHost = HostNormalizer.normalize(host);
    if (normalizedHost.isBlank()) {
      warnings.add("Ignored blank allowed host.");
      return;
    }
    if (normalizedHost.contains("*")) {
      addAllowedWildcardHost(wildcardHosts, warnings, normalizedHost);
      return;
    }
    hosts.add(normalizedHost);
  }

  private static void addAllowedWildcardHost(
      Set<String> wildcardHosts, Set<String> warnings, String host) {
    if (!host.startsWith("*.") || host.indexOf('*', 1) >= 0 || host.length() <= 2) {
      warnings.add("Ignored invalid wildcard host: " + host);
      return;
    }
    String suffix = HostNormalizer.normalize(host.substring(2));
    if (suffix.isBlank() || suffix.contains("*")) {
      warnings.add("Ignored invalid wildcard host: " + host);
      return;
    }
    wildcardHosts.add(suffix);
  }

  private static void addAllowedIp(Set<String> ips, String ip) {
    String normalizedIp = IpNormalizer.normalize(ip);
    if (!normalizedIp.isBlank()) {
      ips.add(normalizedIp);
    }
  }

  private static void addAllowedIpRange(
      Set<CidrRange> ipRanges, Set<String> warnings, String range) {
    CidrRange.parse(range)
        .ifPresentOrElse(
            ipRanges::add, () -> warnings.add("Ignored invalid allowed IP range: " + range));
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
