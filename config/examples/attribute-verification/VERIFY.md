# 動作確認ガイド - Attribute Verification

`setup.sh` のあと、認可画面（app-view）から確認します。サーバーと app-view は、この機能を含むコードで起動しておいてください。

```bash
docker compose up -d --build idp-server-1 idp-server-2 app-view
```

## 1. 認可リクエストを開く

```
https://api.local.test/77777777-0006-7777-7777-777777777777/v1/authorizations?response_type=code&client_id=77777777-0007-7777-7777-777777777777&redirect_uri=http://localhost:3000/callback&scope=openid%20profile%20email&state=test&prompt=login
```

`https://auth.local.test/auth/` のサインイン画面が開き、ステッパーに「Account → Account check → Verification」が表示されます。

## 2. 身元確認が済んでいない利用者

`unverified@attr-verify.example.com` でサインインします（パスワードは `users.json`）。

- 「Account check」に進んだ時点で、入力なしに「Identity verification is required …」と表示される

## 3. 身元確認済みの利用者

`verified@attr-verify.example.com` でサインインします。

- 「Account check」は自動で通る
- 「Verification」で生年月日 `1990-04-01` と電話番号の下 4 桁 `5678` を入力する
- 3 ステップすべてが完了し、同意画面が表示される

違う値を入力すると「The information you entered does not match our records.」と表示されます。
1 回の認可リクエストの中で `kba` の失敗が 5 回に達すると、`lock_conditions` によりアカウントがロックされます。
