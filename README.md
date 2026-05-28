# FQDNGuard

特定の FQDN、例: `mc.example.com`、から接続したプレイヤーのみを許可する Velocity プラグインです。直接 IP アドレスで接続した場合は自動的に拒否しますが、指定した接続元 IP や CIDR 範囲は例外として許可できます。

[English README](README-en.md)

## 要件

- Velocity 3.5.0-SNAPSHOT API 互換のプロキシ
- Java 21+

## ビルド

```sh
./gradlew build
```

プラグイン JAR は `build/libs/FQDNGuard-0.1.0.jar` に生成されます。

## CI

GitHub Actions で Pull Request と `main` への push 時に、JUnit テストと Spotless のフォーマットチェックを実行します。
`main` へのマージを制限するには、GitHub の branch protection rule または ruleset で `Gradle CI / Test and format` を必須ステータスチェックに設定してください。

## 導入

1. 生成された JAR を Velocity の `plugins/` ディレクトリにコピーします。
2. プロキシを一度起動し、`plugins/fqdn-guard/fqdn-guard.yml` を生成します。
3. `allowed-hosts` に接続を許可するドメインを追加します。
4. 必要に応じて、接続元 IP で許可したいアドレスを `allowed-ips`、CIDR 範囲を `allowed-ip-ranges` に追加します。
5. プロキシを再起動します。

設定例:

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

`allowed-hosts` に含まれないホスト名で参加したプレイヤーは、直接 IP 接続を含め、ログイン前に拒否されます。ただし、接続元 IP が `allowed-ips` または `allowed-ip-ranges` に含まれる場合は許可されます。

## 設定

- `allowed-hosts`: 許可する接続先ホスト名。`*.example.com` は `play.example.com` などのサブドメインだけに一致し、`example.com` には一致しません。
- `allowed-ips`: ホスト名の検査を例外許可する接続元 IP アドレス。
- `allowed-ip-ranges`: ホスト名の検査を例外許可する接続元 CIDR 範囲。
- `kick-message`: 理由別メッセージが空の場合の共通メッセージ。
- `kick-message-direct-ip`: 直接 IP 接続を拒否した場合のメッセージ。
- `kick-message-missing-host`: 接続先ホスト名を取得できない場合のメッセージ。
- `kick-message-disallowed-host`: 未許可ホスト名で接続した場合のメッセージ。
- `log-rejections`: 拒否したログイン試行をログに出力します。
- `log-allowed-ip-bypasses`: `allowed-ips` または `allowed-ip-ranges` で許可した接続をログに出力します。

メッセージでは `{host}`、`{allowed_hosts}`、`{remote_ip}`、`{reason}` を利用できます。

## コマンド

- `/fqdnguard status`: 現在読み込まれている許可ルール数を表示します。
- `/fqdnguard reload`: 設定ファイルを再読み込みします。

コマンド権限は `fqdnguard.command` です。エイリアスとして `/fqg` も利用できます。
