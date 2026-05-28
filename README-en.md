# FQDNGuard

Allow connections only when players join via a specific FQDN (e.g., mc.example.com). Direct IP connections are automatically rejected, but configured source IP addresses and CIDR ranges can be allowed as exceptions.

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
4. Add source IP addresses to `allowed-ips` and CIDR ranges to `allowed-ip-ranges` when you want to allow them as exceptions.
5. Restart the proxy.

Example:

```yaml
allowed-hosts:
  - mc.example.com
  - play.example.com
  - "*.example.net"

allowed-ips:
  - 127.0.0.1

allowed-ip-ranges:
  - 192.168.0.0/24

kick-message: "Please connect through {allowed_hosts}."
kick-message-direct-ip: "Direct IP connections are not allowed. Please connect through {allowed_hosts}."
kick-message-missing-host: "Could not verify the connection host. Please connect through {allowed_hosts}."
kick-message-disallowed-host: "Host {host} is not allowed. Please connect through {allowed_hosts}."

log-rejections: true
log-allowed-ip-bypasses: false
```

Players joining through any host not listed in `allowed-hosts`, including direct IP joins, are rejected during pre-login. Connections from IP addresses listed in `allowed-ips` or `allowed-ip-ranges` are allowed.

## Configuration

- `allowed-hosts`: Hostnames players may use when joining. `*.example.com` matches subdomains such as `play.example.com`, but does not match `example.com`.
- `allowed-ips`: Source IP addresses that bypass hostname checks.
- `allowed-ip-ranges`: Source CIDR ranges that bypass hostname checks.
- `kick-message`: Common fallback message when a reason-specific message is empty.
- `kick-message-direct-ip`: Message for direct IP joins.
- `kick-message-missing-host`: Message when the connection host cannot be verified.
- `kick-message-disallowed-host`: Message when the joined host is not allowed.
- `log-rejections`: Log rejected login attempts.
- `log-allowed-ip-bypasses`: Log connections allowed by `allowed-ips` or `allowed-ip-ranges`.

Messages support `{host}`, `{allowed_hosts}`, `{remote_ip}`, and `{reason}`.

## Commands

- `/fqdnguard status`: Show the number of currently loaded allow rules.
- `/fqdnguard reload`: Reload the config file.

The command permission is `fqdnguard.command`. `/fqg` is also available as an alias.
