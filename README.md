# FQDNGuard

特定の FQDN、例: `mc.example.com`、から接続したプレイヤーのみを許可する Velocity プラグインです。直接 IP アドレスで接続した場合は自動的に拒否しますが、指定した接続元 IP は例外として許可できます。

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
4. 必要に応じて、接続元 IP で許可したいアドレスを `allowed-ips` に追加します。
5. プロキシを再起動します。

設定例:

```yaml
allowed-hosts:
  - mc.example.com
  - play.example.com

allowed-ips:
  - 127.0.0.1

kick-message: "Please connect through {allowed_hosts}. Direct IP connections are not allowed."
log-rejections: true
```

`allowed-hosts` に含まれないホスト名で参加したプレイヤーは、直接 IP 接続を含め、ログイン前に拒否されます。ただし、接続元 IP が `allowed-ips` に含まれる場合は許可されます。
