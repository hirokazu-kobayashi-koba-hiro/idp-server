# 共通鍵暗号（対称暗号）

## このドキュメントの目的

**共通鍵暗号（Symmetric Encryption）** の仕組みを理解し、代表的なアルゴリズムと実際の使用例を学びます。

---

## 共通鍵暗号とは

**共通鍵暗号（対称暗号）**:
- 暗号化と復号に**同じ鍵**を使用する
- 処理が高速で、大量のデータ暗号化に適している
- 鍵の安全な共有が課題

```
[送信者]                           [受信者]
   │                                   │
   │ ──── 同じ秘密鍵を共有 ────→       │
   │                                   │
   │ 「Hello」                         │
   │    ↓ 暗号化（秘密鍵使用）         │
   │ 「X8f2kL9」                       │
   │ ──── 暗号文を送信 ────────→       │
   │                                   │
   │                          「X8f2kL9」
   │                             ↓ 復号（同じ秘密鍵）
   │                          「Hello」
```

---

## 代表的なアルゴリズム

### AES（Advanced Encryption Standard）

**最も広く使われている共通鍵暗号**

```
特徴:
- 2001年にNISTが標準化
- ブロックサイズ: 128ビット
- 鍵長: 128/192/256ビット
- 高速で安全性が高い

使用例:
- HTTPS/TLS通信
- ファイル暗号化
- データベース暗号化
- VPN
```

#### 鍵長による強度

| 鍵長 | 安全性 | 用途 |
|------|--------|------|
| AES-128 | 高い | 一般的な用途 |
| AES-192 | より高い | 機密性の高いデータ |
| AES-256 | 最も高い | 政府・軍事レベル |

---

### ChaCha20

**モバイル環境で人気のストリーム暗号**

```
特徴:
- Google開発
- ストリーム暗号（ブロック暗号ではない）
- ハードウェアアクセラレーションなしでも高速
- TLS 1.3で採用

AESとの比較:
┌─────────────┬─────────────────┬─────────────────┐
│             │      AES        │    ChaCha20     │
├─────────────┼─────────────────┼─────────────────┤
│ 種類        │ ブロック暗号    │ ストリーム暗号  │
│ HW支援      │ 必要（高速化）  │ 不要            │
│ モバイル    │ やや遅い        │ 高速            │
│ 採用        │ TLS, IPsec等    │ TLS 1.3, WireGuard │
└─────────────┴─────────────────┴─────────────────┘
```

---

## ブロック暗号の動作モード

AESのようなブロック暗号は、固定サイズ（128ビット）のブロック単位で処理します。
長いデータを暗号化するには「動作モード」が必要です。

### ECB（Electronic Codebook）- 使用禁止

```
❌ ECBモード（使ってはいけない）

同じ平文ブロック → 同じ暗号文ブロック

平文:  [ブロック1][ブロック1][ブロック2]
暗号文: [暗号A   ][暗号A   ][暗号B   ]
                 ↑ 同じになってしまう！

問題点:
- パターンが漏れる
- 有名な「ペンギン問題」：画像を暗号化しても輪郭が見える
```

### CBC（Cipher Block Chaining）

```
⚠️ CBCモード（レガシー、新規では非推奨）

前のブロックの暗号文を次のブロックの暗号化に使用

平文1 ──→ XOR ──→ 暗号化 ──→ 暗号文1
            ↑                      │
           IV                      ↓
平文2 ──→ XOR ──→ 暗号化 ──→ 暗号文2
            ↑                      │
        暗号文1                    ↓
                              ...

IV (Initialization Vector):
- 最初のブロック用のランダム値
- 暗号文と一緒に送信
- 秘密にする必要はないが、予測不可能であること

問題点:
- パディングオラクル攻撃に脆弱
- 並列処理ができない
```

### GCM（Galois/Counter Mode）- 推奨

```
⭕ GCMモード（現在の推奨）

暗号化 + 認証を同時に行う（AEAD）

特徴:
- 暗号化と同時にMAC（認証タグ）を生成
- 改ざん検知が可能
- 並列処理が可能で高速

構成:
┌────────────────────────────────────────┐
│              AES-GCM                    │
├────────────────────────────────────────┤
│  入力: 平文 + 追加認証データ(AAD)       │
│  出力: 暗号文 + 認証タグ(128ビット)     │
└────────────────────────────────────────┘

追加認証データ(AAD):
- 暗号化しないが認証したいデータ
- 例: HTTPヘッダー、メタデータ
```

---

## AEAD（Authenticated Encryption with Associated Data）

**現代の暗号化では必須の概念**

```
従来の暗号化:
  暗号化のみ → 改ざんされても気づかない

AEAD:
  暗号化 + 認証 → 改ざんを検知できる

┌─────────────────────────────────────────────┐
│                   AEAD                       │
├─────────────────────────────────────────────┤
│  機密性: データを読めなくする               │
│  完全性: データが改ざんされていないことを確認 │
│  認証:   正しい送信者からのデータであることを確認 │
└─────────────────────────────────────────────┘

代表的なAEAD:
- AES-GCM（最も一般的）
- ChaCha20-Poly1305（モバイル向け）
- AES-CCM（IoT向け、メモリ効率が良い）
```

---

## 実装例

### Java（AES-GCM）

```java
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.SecureRandom;

public class AesGcmExample {

    private static final int GCM_IV_LENGTH = 12;  // 96ビット（推奨）
    private static final int GCM_TAG_LENGTH = 128; // 認証タグ長

    // 暗号化
    public static byte[] encrypt(byte[] plaintext, SecretKey key) throws Exception {
        // ランダムなIVを生成
        byte[] iv = new byte[GCM_IV_LENGTH];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);

        // 暗号化
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);

        byte[] ciphertext = cipher.doFinal(plaintext);

        // IV + 暗号文を結合して返す
        byte[] result = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);

        return result;
    }

    // 復号
    public static byte[] decrypt(byte[] ciphertextWithIv, SecretKey key) throws Exception {
        // IVを抽出
        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(ciphertextWithIv, 0, iv, 0, iv.length);

        // 暗号文を抽出
        byte[] ciphertext = new byte[ciphertextWithIv.length - iv.length];
        System.arraycopy(ciphertextWithIv, iv.length, ciphertext, 0, ciphertext.length);

        // 復号
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);

        return cipher.doFinal(ciphertext);
    }

    // 鍵生成
    public static SecretKey generateKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256); // AES-256
        return keyGen.generateKey();
    }
}
```

### JavaScript（Web Crypto API）

```javascript
// 鍵生成
async function generateKey() {
    return await crypto.subtle.generateKey(
        { name: "AES-GCM", length: 256 },
        true,  // extractable
        ["encrypt", "decrypt"]
    );
}

// 暗号化
async function encrypt(plaintext, key) {
    const iv = crypto.getRandomValues(new Uint8Array(12)); // 96ビットIV
    const encoded = new TextEncoder().encode(plaintext);

    const ciphertext = await crypto.subtle.encrypt(
        { name: "AES-GCM", iv: iv },
        key,
        encoded
    );

    // IV + 暗号文を結合
    const result = new Uint8Array(iv.length + ciphertext.byteLength);
    result.set(iv);
    result.set(new Uint8Array(ciphertext), iv.length);

    return result;
}

// 復号
async function decrypt(ciphertextWithIv, key) {
    const iv = ciphertextWithIv.slice(0, 12);
    const ciphertext = ciphertextWithIv.slice(12);

    const decrypted = await crypto.subtle.decrypt(
        { name: "AES-GCM", iv: iv },
        key,
        ciphertext
    );

    return new TextDecoder().decode(decrypted);
}
```

---

## 鍵の課題

### 鍵配送問題

```
共通鍵暗号の最大の課題: どうやって鍵を安全に共有するか？

[送信者]                           [受信者]
   │                                   │
   │ ─── 秘密鍵をどう送る？ ───→      │
   │        ↑                          │
   │    [攻撃者] ← 傍受されたら終わり！ │

解決策:
1. 公開鍵暗号で鍵交換（次章で説明）
2. 事前に安全な方法で共有（物理的な受け渡しなど）
3. 鍵導出関数（KDF）で共有シークレットから生成
```

### 鍵の管理

```
⚠️ 重要な注意点

1. 鍵をコードにハードコードしない
   ❌ private static final String KEY = "mysecretkey123";
   ⭕ 環境変数や鍵管理システムから取得

2. 鍵を定期的にローテーション
   - 暗号化されたデータに鍵IDを含める
   - 古いデータは再暗号化するか、古い鍵も保持

3. 安全な乱数生成器を使用
   ❌ new Random()
   ⭕ new SecureRandom()
```

---

## アイデンティティ管理での使用例

### 1. セッションデータの暗号化

```
ブラウザに保存するセッション情報を暗号化

[サーバー]
   │
   │ セッションデータをAES-GCMで暗号化
   │ 暗号化されたCookieを送信
   │
   ↓
[ブラウザ] ← Cookieを保存（読めない）
   │
   │ リクエスト時にCookieを送信
   │
   ↓
[サーバー]
   │
   │ AES-GCMで復号
   │ セッションデータを取得
```

### 2. データベース暗号化

```
保存時に暗号化、読み取り時に復号

[アプリケーション]
   │
   │ 個人情報をAESで暗号化
   │
   ↓
[データベース]
   │
   │ 暗号化されたデータを保存
   │ （流出しても読めない）
```

### 3. トークンの暗号化

```
JWE（JSON Web Encryption）

ヘッダー.暗号化キー.IV.暗号文.認証タグ

Content Encryption Key (CEK)をAES-GCMで暗号化
ペイロードをCEKで暗号化
```

---

## セキュリティの注意点

### やってはいけないこと

| ❌ 悪い例 | ⭕ 良い例 |
|----------|----------|
| ECBモードを使用 | GCMモードを使用 |
| 固定IVを使用 | 毎回ランダムIVを生成 |
| 弱い鍵（短い、予測可能） | 256ビットのランダム鍵 |
| `Random`で鍵生成 | `SecureRandom`で鍵生成 |
| 鍵をログに出力 | 鍵は絶対にログに出さない |

### IVの再利用は致命的

```
⚠️ AES-GCMでIVを再利用すると:

同じ鍵 + 同じIV + 異なる平文
   ↓
暗号文のXORで平文のXORが得られる
   ↓
情報漏洩！

対策:
- 暗号化ごとに新しいIVを生成
- カウンターベースIV（適切に管理する場合）
```

---

## まとめ

共通鍵暗号のポイント:

1. **AES-GCMが現在の推奨** - 暗号化と認証を同時に行う
2. **ECBは使用禁止** - パターンが漏れる
3. **IVは毎回新しく生成** - 再利用は致命的
4. **鍵配送が課題** - 公開鍵暗号と組み合わせる
5. **鍵管理を慎重に** - ハードコード禁止、ローテーション必須

次のドキュメントでは、鍵配送問題を解決する公開鍵暗号について学びます。
