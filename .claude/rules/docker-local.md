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

## コンテナを再作成したら `load-balancer` も再起動する

nginx は upstream のホスト名を**起動時に一度だけ**解決して IP を保持する。コンテナを再作成すると
IP が変わり、しかも他のコンテナが元の IP を引き継ぐことがあるため、**nginx が別のコンテナへ
中継し続ける**。

```bash
docker compose up -d --build app-view app-view-crosssite app-view-context-path
docker restart load-balancer   # これを忘れない
```

実際に踏んだ症状: `auth.idp.local`（本来 `app-view-crosssite`）が `app-view-context-path` の
ビルドを配信し、画面が `https://api.local.test/idp-admin/...` を叩いて全リクエストが 401 になった。
**コンテナの中身は正しく、配信されているものだけが違う**ので気づきにくい。

疑わしいときは、配信されているチャンクとコンテナ内のファイルを突き合わせる。

```bash
curl -sk https://auth.idp.local/auth/ | grep -oE '_app-[a-z0-9]+\.js'
docker exec app-view-crosssite ls out/_next/static/chunks/pages/_app-*.js
# 食い違っていたら nginx が別コンテナを向いている
```

## どの画面をどのコンテナが配信するか

`app-view` 系は 3 つあり、nginx の `server_name` で振り分けている（`docker/nginx/nginx.conf`）。
テナントの `ui_config.base_url` がどれを指すかで、再ビルドすべきコンテナが変わる。

| ホスト名 | コンテナ | ビルド時の `NEXT_PUBLIC_BACKEND_URL` |
|---|---|---|
| `auth.local.test` | `app-view` | `https://api.local.test` |
| `auth.idp.local` | `app-view-crosssite` | `https://api.local.test` |
| `auth-cp.idp.local` | `app-view-context-path` | `https://api.local.test/idp-admin` |

`backendUrl` はビルド時に埋め込まれる（`process.env.NEXT_PUBLIC_BACKEND_URL`）ので、実行時の
環境変数を見ても実際に使われている値は分からない。配信されたチャンクを見ること。
