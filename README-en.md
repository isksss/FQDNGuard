# FQDNGuard

Allow connections only when players join via a specific FQDN (e.g., mc.example.com). Direct IP connections are automatically rejected, but configured source IP addresses can be allowed as exceptions.

[日本語 README](README.md)

## Requirements

- Velocity 3.5.0-SNAPSHOT API compatible proxy
- Java 21+

## Build

```sh
./gradlew build
```

The plugin jar is created at `build/libs/FQDNGuard-0.1.0.jar`.

## CI

GitHub Actions runs JUnit tests and Spotless formatting checks on pull requests and pushes to `main`.
To block merges into `main`, configure a GitHub branch protection rule or ruleset and require the `Gradle CI / Test and format` status check.

## Install

1. Copy the jar to the Velocity `plugins/` directory.
2. Start the proxy once to generate `plugins/fqdn-guard/fqdn-guard.yml`.
3. Add allowed domains to `allowed-hosts`.
4. Add source IP addresses to `allowed-ips` when you want to allow them as exceptions.
5. Restart the proxy.

Example:

```yaml
allowed-hosts:
  - mc.example.com
  - play.example.com

allowed-ips:
  - 127.0.0.1

kick-message: "Please connect through {allowed_hosts}. Direct IP connections are not allowed."
log-rejections: true
```

Players joining through any host not listed in `allowed-hosts`, including direct IP joins, are rejected during pre-login. Connections from IP addresses listed in `allowed-ips` are allowed.
