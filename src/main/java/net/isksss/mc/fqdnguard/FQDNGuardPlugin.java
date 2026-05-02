package net.isksss.mc.fqdnguard;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import java.io.IOException;
import java.io.Reader;
import java.net.IDN;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
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
  private static final String CONFIG_FILE_NAME = "fqdn-guard.properties";
  private static final String DEFAULT_ALLOWED_HOST = "mc.example.com";
  private static final String DEFAULT_KICK_MESSAGE =
      "Please connect through {allowed_hosts}. Direct IP connections are not allowed.";

  private final Logger logger;
  private final Path dataDirectory;

  private Set<String> allowedHosts = Collections.emptySet();
  private String kickMessage = DEFAULT_KICK_MESSAGE;
  private boolean logRejections = true;

  @Inject
  public FQDNGuardPlugin(Logger logger, @DataDirectory Path dataDirectory) {
    this.logger = logger;
    this.dataDirectory = dataDirectory;
  }

  @Subscribe
  public void onProxyInitialize(ProxyInitializeEvent event) {
    loadConfig();
  }

  @Subscribe
  public void onPreLogin(PreLoginEvent event) {
    Optional<String> virtualHost =
        event
            .getConnection()
            .getVirtualHost()
            .map(InetSocketAddress::getHostString)
            .map(FQDNGuardPlugin::normalizeHost);

    if (virtualHost.isPresent() && allowedHosts.contains(virtualHost.get())) {
      return;
    }

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

  private void loadConfig() {
    Path configPath = dataDirectory.resolve(CONFIG_FILE_NAME);
    try {
      Files.createDirectories(dataDirectory);
      if (Files.notExists(configPath)) {
        Files.writeString(configPath, defaultConfig(), StandardCharsets.UTF_8);
      }

      Properties properties = new Properties();
      try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
        properties.load(reader);
      }

      Set<String> configuredHosts =
          parseAllowedHosts(properties.getProperty("allowed-hosts", DEFAULT_ALLOWED_HOST));
      if (configuredHosts.isEmpty()) {
        logger.warn("No allowed hosts configured. Falling back to '{}'.", DEFAULT_ALLOWED_HOST);
        configuredHosts = Set.of(DEFAULT_ALLOWED_HOST);
      }

      allowedHosts = configuredHosts;
      kickMessage = properties.getProperty("kick-message", DEFAULT_KICK_MESSAGE);
      logRejections = Boolean.parseBoolean(properties.getProperty("log-rejections", "true"));

      logger.info("FQDNGuard enabled. Allowed hosts: {}", String.join(", ", allowedHosts));
    } catch (IOException exception) {
      allowedHosts = Set.of(DEFAULT_ALLOWED_HOST);
      kickMessage = DEFAULT_KICK_MESSAGE;
      logRejections = true;
      logger.error(
          "Failed to load FQDNGuard config. Falling back to '{}'.",
          DEFAULT_ALLOWED_HOST,
          exception);
    }
  }

  private String formatKickMessage(String host) {
    return kickMessage
        .replace("{host}", host)
        .replace("{allowed_hosts}", String.join(", ", allowedHosts));
  }

  private static Set<String> parseAllowedHosts(String rawValue) {
    Set<String> hosts = new LinkedHashSet<>();
    Arrays.stream(rawValue.split(","))
        .map(FQDNGuardPlugin::normalizeHost)
        .filter(value -> !value.isBlank())
        .forEach(hosts::add);
    return Collections.unmodifiableSet(hosts);
  }

  private static String normalizeHost(String host) {
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

  private static String defaultConfig() {
    return """
        # Comma-separated FQDNs that players are allowed to use when joining.
        # Example: allowed-hosts=mc.example.com,play.example.com
        allowed-hosts=mc.example.com

        # Placeholders: {host}, {allowed_hosts}
        kick-message=Please connect through {allowed_hosts}. Direct IP connections are not allowed.

        # Log rejected connection attempts to the proxy console.
        log-rejections=true
        """;
  }
}
