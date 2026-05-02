# FQDNGuard

Allow connections only when players join via a specific FQDN (e.g., mc.example.com). Direct IP connections automatically rejected.

[日本語 README](README.md)

## Requirements

- Velocity 3.5.0-SNAPSHOT API compatible proxy
- Java 21+

## Build

```sh
gradle build
```

The plugin jar is created at `build/libs/FQDNGuard-0.1.0.jar`.

## Install

1. Copy the jar to the Velocity `plugins/` directory.
2. Start the proxy once to generate `plugins/fqdn-guard/fqdn-guard.yml`.
3. Add allowed domains to `allowed-hosts`.
4. Restart the proxy.

Example:

```yaml
allowed-hosts:
  - mc.example.com
  - play.example.com

kick-message: "Please connect through {allowed_hosts}. Direct IP connections are not allowed."
log-rejections: true
```

Players joining through any host not listed in `allowed-hosts`, including direct IP joins, are rejected during pre-login.
