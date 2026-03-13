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
