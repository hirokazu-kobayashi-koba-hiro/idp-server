# ハッシュ関数

## このドキュメントの目的

**ハッシュ関数（Hash Functions）** の仕組みを理解し、パスワード保護や完全性検証での使い方を学びます。

---

## ハッシュ関数とは

**ハッシュ関数**:
- 任意の長さのデータを**固定長の値（ハッシュ値）** に変換
- **一方向性**: ハッシュ値から元のデータを復元できない
- **決定的**: 同じ入力は常に同じハッシュ値

```
入力（任意の長さ）        ハッシュ関数        出力（固定長）
─────────────────────────────────────────────────────────
"Hello"             →    SHA-256    →   2cf24dba5fb0a30e...
"Hello World"       →    SHA-256    →   a591a6d40bf42040...
（10MBのファイル）  →    SHA-256    →   c3ab8ff13720e8ad...
                                         ↑
                                    常に256ビット（64文字）
```

---

## ハッシュ関数の3つの特性

### 1. 一方向性（Preimage Resistance）

```
ハッシュ値から元のデータを計算できない

元データ ──→ ハッシュ値
   ?    ←─×─ ハッシュ値

例:
"password123" → 482c811da5d5b4...
    ↑
この値を見ても "password123" とはわからない
```

### 2. 衝突耐性（Collision Resistance）

```
異なる2つの入力が同じハッシュ値を生成することが困難

入力A → ハッシュ値X
入力B → ハッシュ値X  ← これを見つけるのが困難

※理論的には無限の入力を有限の出力にマッピングするため衝突は存在するが、
  実用上見つけることは不可能
```

### 3. 雪崩効果（Avalanche Effect）

```
入力が少し変わるとハッシュ値が大きく変わる

"Hello"  → 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
"hello"  → 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9825
            ↑                                                               ↑
         1文字だけ違う                                            全く異なるハッシュ

※実際はほぼ全ての文字が変わります
```

---

## 代表的なハッシュアルゴリズム

### MD5 - 使用禁止

```
❌ MD5（Message Digest 5）

出力長: 128ビット（32文字）
状態: 衝突が発見されており、セキュリティ用途では使用禁止

例:
echo -n "password" | md5
→ 5f4dcc3b5aa765d61d8327deb882cf99

脆弱性:
- 2004年に衝突が発見
- 2008年にSSL証明書の偽造に成功
- 現在は数秒で衝突を生成可能

用途（非セキュリティ）:
- ファイルの簡易チェックサム
- キャッシュのキー生成
```

### SHA-1 - 非推奨

```
⚠️ SHA-1（Secure Hash Algorithm 1）

出力長: 160ビット（40文字）
状態: 衝突が発見されており、新規使用は非推奨

例:
echo -n "password" | shasum
→ 5baa61e4c9b93f3f0682250b6cf8331b7ee68fd8

脆弱性:
- 2017年にGoogleが衝突を公開（SHAttered攻撃）
- 証明書やGit署名での使用は段階的に廃止

移行:
- 多くのシステムがSHA-256への移行を完了
```

### SHA-256 - 推奨

```
⭕ SHA-256（SHA-2ファミリー）

出力長: 256ビット（64文字）
状態: 現在の標準、安全

例:
echo -n "password" | shasum -a 256
→ 5e884898da28047d9f0d27f87d9c4e79bcc1e0e4d7f4b3b7c8e9f0a1b2c3d4e5

特徴:
- 2001年にNISTが標準化
- Bitcoin、TLS、JWT署名など広く使用
- 計算が高速
```

### SHA-3 - 最新

```
⭕ SHA-3（Keccak）

出力長: 224/256/384/512ビット
状態: SHA-2の代替として2015年に標準化

特徴:
- SHA-2とは全く異なる内部構造（スポンジ構造）
- SHA-2が破られた場合のバックアップ
- 可変長出力のSHAKE128/256も提供
```

---

## パスワードハッシュ

### なぜ専用のハッシュが必要か

```
一般的なハッシュ（SHA-256）の問題:

問題1: 高速すぎる
  - GPUで1秒間に数十億回計算可能
  - 総当たり攻撃が容易

問題2: レインボーテーブル攻撃
  - 事前計算された「パスワード→ハッシュ」の表
  - 一般的なパスワードは瞬時に解読

パスワードハッシュの要件:
1. 意図的に遅い（計算コストが高い）
2. ソルト（塩）を使用
3. メモリを大量に消費（GPU攻撃対策）
```

### ソルト（Salt）

```
ソルト: ハッシュ前にパスワードに追加するランダムな値

ソルトなし:
password123 → abc123...
password123 → abc123...  ← 同じハッシュ！

ソルトあり:
password123 + salt1 → xyz789...
password123 + salt2 → def456...  ← 異なるハッシュ！

ソルトの保存:
┌───────────────────────────────────────────┐
│  $2b$12$LQv3c1yqBWVHxkd0Ljg...           │
│   ↑  ↑   ↑                               │
│   │  │   └── ソルト + ハッシュ            │
│   │  └───── コストファクター              │
│   └──────── アルゴリズム（bcrypt）        │
└───────────────────────────────────────────┘
```

### bcrypt - 推奨

```
⭕ bcrypt

特徴:
- 1999年から使用されている実績
- コストファクターで計算時間を調整可能
- ソルトが自動的に含まれる
- 72バイトの入力制限あり

フォーマット:
$2b$12$LQv3c1yqBWVHxkd0LjgGeO0CIMzThMQxQMZoQJdNGpCZON3E9cVw.
 │  │  │                    │
 │  │  │                    └── ハッシュ値
 │  │  └───────────────────── ソルト（22文字）
 │  └────────────────────── コストファクター（2^12回の反復）
 └────────────────────────── アルゴリズムバージョン

コストファクターの目安:
- 10: 約100ms（最低限）
- 12: 約400ms（推奨）
- 14: 約1.5秒（高セキュリティ）
```

### Argon2 - 最新推奨

```
⭕ Argon2

特徴:
- 2015年 Password Hashing Competition 優勝
- メモリハードネス（GPUでの並列攻撃に強い）
- 3つのバリアント: Argon2i, Argon2d, Argon2id

バリアント:
┌─────────────┬────────────────────────────────┐
│ Argon2d     │ GPU攻撃に最も強い               │
│             │ サイドチャネル攻撃に弱い         │
├─────────────┼────────────────────────────────┤
│ Argon2i     │ サイドチャネル攻撃に強い         │
│             │ GPU攻撃への耐性がやや低い        │
├─────────────┼────────────────────────────────┤
│ Argon2id    │ 両方のハイブリッド（推奨）       │
│             │ 一般的なパスワードハッシュに最適  │
└─────────────┴────────────────────────────────┘

パラメータ:
- time: 反復回数（3以上推奨）
- memory: メモリ使用量（64MB以上推奨）
- parallelism: 並列度
```

### PBKDF2 - レガシー

```
△ PBKDF2（Password-Based Key Derivation Function 2）

特徴:
- NIST推奨（FIPS準拠が必要な場合）
- 反復回数で計算コストを調整
- ソルトを使用
- メモリハードネスなし（GPU攻撃に弱い）

推奨設定（OWASP 2023）:
- PBKDF2-HMAC-SHA256: 600,000回以上
- PBKDF2-HMAC-SHA512: 210,000回以上

bcrypt/Argon2が使えない場合の代替
```

---

## 実装例

### Java（bcrypt）

```java
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordHashExample {

    private static final BCryptPasswordEncoder encoder =
        new BCryptPasswordEncoder(12); // コストファクター12

    // パスワードをハッシュ化
    public static String hashPassword(String password) {
        return encoder.encode(password);
    }

    // パスワードを検証
    public static boolean verifyPassword(String password, String hash) {
        return encoder.matches(password, hash);
    }
}
```

### Java（Argon2）

```java
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import java.security.SecureRandom;

public class Argon2Example {

    public static byte[] hashPassword(String password, byte[] salt) {
        int iterations = 3;
        int memoryKB = 65536; // 64MB
        int parallelism = 4;
        int hashLength = 32;

        Argon2Parameters.Builder builder = new Argon2Parameters.Builder(
            Argon2Parameters.ARGON2_id)
            .withSalt(salt)
            .withIterations(iterations)
            .withMemoryAsKB(memoryKB)
            .withParallelism(parallelism);

        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(builder.build());

        byte[] hash = new byte[hashLength];
        generator.generateBytes(password.toCharArray(), hash);

        return hash;
    }

    public static byte[] generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return salt;
    }
}
```

### JavaScript（bcrypt）

```javascript
const bcrypt = require('bcrypt');

const SALT_ROUNDS = 12;

// パスワードをハッシュ化
async function hashPassword(password) {
    return await bcrypt.hash(password, SALT_ROUNDS);
}

// パスワードを検証
async function verifyPassword(password, hash) {
    return await bcrypt.compare(password, hash);
}
```

---

## HMAC（Hash-based Message Authentication Code）

**メッセージ認証コード: ハッシュ + 秘密鍵**

```
HMACの仕組み:

メッセージ + 秘密鍵 → HMAC → 認証コード

用途:
- メッセージの完全性と認証を同時に確認
- APIリクエストの署名
- JWTのHS256署名

計算方法:
HMAC(K, m) = H((K' ⊕ opad) || H((K' ⊕ ipad) || m))

K: 秘密鍵
m: メッセージ
H: ハッシュ関数
opad, ipad: 定数
```

### 実装例

```java
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class HmacExample {

    public static String calculateHmac(String message, byte[] key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(key, "HmacSHA256");
        mac.init(secretKey);
        byte[] hmacBytes = mac.doFinal(message.getBytes());
        return Base64.getEncoder().encodeToString(hmacBytes);
    }

    public static boolean verifyHmac(String message, String expectedHmac, byte[] key)
            throws Exception {
        String calculatedHmac = calculateHmac(message, key);
        // タイミング攻撃を防ぐため、定数時間で比較
        return MessageDigest.isEqual(
            calculatedHmac.getBytes(),
            expectedHmac.getBytes()
        );
    }
}
```

---

## アイデンティティ管理での使用例

### 1. パスワード保存

```
ユーザー登録時:
password123 + salt → bcrypt → $2b$12$...

ログイン時:
入力されたパスワード + 保存されたソルト → bcrypt
計算結果と保存されたハッシュを比較

⚠️ 注意点:
- パスワードは絶対に平文で保存しない
- ハッシュは復号できないので、パスワードリセットは新規発行
```

### 2. トークンの完全性検証

```
JWT（HS256）:

ヘッダー.ペイロード.署名
         ↓
   HMAC-SHA256で署名を計算
         ↓
   計算結果と署名を比較

署名が一致 → トークンは改ざんされていない
```

### 3. CSRFトークン

```
セッション固有のシークレット + ランダム値 → HMAC → CSRFトークン

リクエスト時:
- フォームに埋め込まれたCSRFトークンを送信
- サーバーで再計算して一致を確認
```

### 4. 認可コード（authorization code）

```
認可コード + PKCE:

code_verifier: ランダムな43〜128文字の文字列
code_challenge: Base64URL(SHA256(code_verifier))

認可リクエスト: code_challenge を送信
トークンリクエスト: code_verifier を送信
サーバー: SHA256(code_verifier) == code_challenge を検証
```

---

## ハッシュアルゴリズムの選択

| 用途 | 推奨アルゴリズム |
|------|-----------------|
| パスワード保存 | Argon2id > bcrypt > PBKDF2 |
| ファイル完全性 | SHA-256 |
| メッセージ認証（HMAC） | HMAC-SHA256 |
| デジタル署名（内部） | SHA-256 / SHA-384 |
| 非セキュリティ用途 | SHA-256（またはMD5） |

---

## セキュリティの注意点

### やってはいけないこと

| ❌ 悪い例 | ⭕ 良い例 |
|----------|----------|
| MD5/SHA-1をセキュリティ用途に使用 | SHA-256以上を使用 |
| パスワードにSHA-256を直接使用 | bcrypt/Argon2を使用 |
| 固定ソルトを使用 | ユーザーごとにランダムソルト |
| HMAC検証で`==`を使用 | 定数時間比較を使用 |
| 短いコストファクター | 100ms以上かかる設定 |

### レインボーテーブル対策

```
レインボーテーブル攻撃:

事前計算された表:
password → 5f4dcc3b...
123456 → e10adc39...
qwerty → d8578edf...

対策:
1. ソルトを使用（ユーザーごとに異なる）
2. パスワードハッシュ専用関数を使用
3. 十分なコストファクター
```

---

## まとめ

ハッシュ関数のポイント:

1. **一方向性** - 元に戻せない
2. **用途に応じたアルゴリズム選択**:
   - パスワード: Argon2id / bcrypt
   - 完全性: SHA-256
   - 認証: HMAC-SHA256
3. **MD5/SHA-1は非推奨** - セキュリティ用途では使用禁止
4. **パスワードには専用関数** - SHA-256は使わない
5. **ソルトは必須** - レインボーテーブル対策

次のドキュメントでは、デジタル署名について学びます。
