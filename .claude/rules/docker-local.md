---
paths:
  - "docker-compose*.yml"
  - "docker-compose*.yaml"
  - "Dockerfile*"
  - "docker/**"
---

# Docker / ローカル環境のルール

## コード変更後は `--build` フラグ必須

Javaコードを変更した場合、Docker imageを再ビルドしないと変更が反映されない。

```bash
# OK: imageを再ビルドして反映
docker compose up -d --build idp-server-1 idp-server-2

# NG: 古いimageのまま起動
docker compose restart idp-server-1
```

- Dockerfileがマルチステージビルドのため、`./gradlew bootJar` は不要
- `docker compose restart` だけでは新しいコードは反映されない

## バインドマウントの設定変更は `restart` が必要

`docker/mockoon/config.json` のように、コンテナへバインドマウントしている設定ファイルを変更した場合は `restart` する。`up -d` は compose 定義に差分が無いとコンテナを再作成しないため、ファイルを書き換えても「Running」のまま古い設定が読み込まれ続ける。

```bash
# OK: プロセスが設定を読み直す
docker compose restart mockoon

# NG: 「Running」と表示されるだけで再読み込みされない
docker compose up -d --build mockoon
```

`--build` フラグ必須ルールと逆になるのは、対象がイメージに焼き込まれたコードではなくマウント済みファイルのため。
