# クレデンシャル攻撃と対策

## このドキュメントの目的

ID/認証システムに対する**クレデンシャル攻撃**（認証情報を狙った攻撃）の種類と、効果的な対策を学びます。

---

## クレデンシャル攻撃の種類

| 攻撃手法 | 説明 | 成功率 | 検知難易度 |
|---------|------|--------|-----------|
| ブルートフォース | 総当たり攻撃 | 低（対策あれば） | 容易 |
| 辞書攻撃 | よく使われるパスワードを試行 | 中 | 容易 |
| クレデンシャルスタッフィング | 流出した認証情報を使用 | 高 | 困難 |
| パスワードスプレー | 多数のアカウントに少数のパスワード | 中 | 困難 |
| フィッシング | 偽サイトで認証情報を詐取 | 高 | 対象外 |
| キーロガー | 入力を記録 | 高 | 対象外 |

### ブルートフォース vs パスワードスプレーの違い

```
【ブルートフォース】               【パスワードスプレー】
1つのアカウントに                 多数のアカウントに
多数のパスワードを試行             少数のパスワードを試行

  user1                            user1  user2  user3  user4  ...
    │                                │      │      │      │
    ├─ pass001 ✗                     └──────┴──────┴──────┴─── Password1! ✗✗✗✓
    ├─ pass002 ✗
    ├─ pass003 ✗                   （1時間後）
    ├─ pass004 ✗                     │      │      │      │
    ├─ ...                           └──────┴──────┴──────┴─── Summer2024 ✗✓✗✗
    └─ pass999 ✓

→ アカウントロックで              → 各アカウントの試行は1-2回
  すぐ検知される                    検知されにくい
```

---

## ブルートフォース攻撃

### 攻撃の仕組み

```
攻撃者: すべてのパスワードの組み合わせを試行

例: 4桁の数字PIN
0000 → 失敗
0001 → 失敗
0002 → 失敗
...
1234 → 成功！

試行回数: 最大10,000回
```

### 計算量

| パスワード長 | 文字種 | 組み合わせ数 | 解読時間（毎秒1万試行） |
|------------|--------|-------------|---------------------|
| 4桁 | 数字のみ | 10,000 | 1秒 |
| 6文字 | 小文字のみ | 約3億 | 8時間 |
| 8文字 | 英数字 | 約218兆 | 700年 |
| 12文字 | 英数字記号 | 約10^23 | 天文学的時間 |

### 対策

**1. 試行回数制限（Rate Limiting）**
```yaml
# 設定例
authentication_policy:
  rate_limit:
    max_attempts_per_minute: 10
    max_attempts_per_hour: 100
```

**2. アカウントロックアウト**
```yaml
authentication_policy:
  lockout:
    max_failed_attempts: 5
    lockout_duration_seconds: 900  # 15分
    reset_after_success: true
```

**3. プログレッシブディレイ**
```
1回目失敗: 即座にレスポンス
2回目失敗: 1秒待機
3回目失敗: 2秒待機
4回目失敗: 4秒待機
5回目失敗: アカウントロック
```

```java
// 実装例
int delay = (int) Math.pow(2, failedAttempts - 1) * 1000;
Thread.sleep(Math.min(delay, 30000)); // 最大30秒
```

---

## 辞書攻撃

### 攻撃の仕組み

```
攻撃者: よく使われるパスワードのリストを使用

password
123456
qwerty
admin
letmein
welcome
...

流出データから収集された数百万のパスワードリストを使用
```

### よく使われるパスワード（2024年統計）

```
1. 123456
2. password
3. 12345678
4. qwerty
5. 123456789
6. 12345
7. 1234
8. 111111
9. 1234567
10. dragon
```

### 対策

**1. 禁止パスワードリスト**
```java
// 実装例
Set<String> bannedPasswords = loadBannedPasswordList();

public void validatePassword(String password) {
    if (bannedPasswords.contains(password.toLowerCase())) {
        throw new WeakPasswordException(
            "This password is too common. Please choose a different one."
        );
    }
}
```

**2. パスワード強度チェック**
```yaml
password_policy:
  min_length: 12
  require_uppercase: true
  require_lowercase: true
  require_number: true
  require_special: true
  banned_passwords_file: /config/banned-passwords.txt
  check_leaked_passwords: true  # Have I Been Pwned API
```

---

## クレデンシャルスタッフィング

### 攻撃の仕組み

```
前提: 他のサービスから流出した認証情報を入手

流出データ例:
user1@example.com : password123
user2@example.com : qwerty456
user3@example.com : letmein789

攻撃:
1. ターゲットサイトに流出した認証情報でログイン試行
2. 多くのユーザーが同じパスワードを使い回している
3. 一定割合でログイン成功
```

### 統計

```
- 平均的なユーザーは5つのサービスで同じパスワードを使用
- 流出認証情報の約0.1-2%が他のサービスでも有効
- 1億件の流出データ → 10万〜200万アカウント侵害の可能性
```

### 対策

**1. 流出パスワードチェック（Have I Been Pwned API）**
```java
// k-Anonymity を使用した安全なチェック
public boolean isPasswordBreached(String password) {
    String sha1 = sha1Hash(password);
    String prefix = sha1.substring(0, 5);
    String suffix = sha1.substring(5).toUpperCase();

    // APIにはハッシュの先頭5文字のみ送信
    String response = httpClient.get(
        "https://api.pwnedpasswords.com/range/" + prefix
    );

    // 返されたリストに完全なハッシュがあるかチェック
    return response.contains(suffix);
}
```

**2. 異常検知**
```
監視すべき指標:
- 異なるユーザーへの同一IPからのログイン試行
- 短時間での多数のログイン失敗
- 通常と異なる地理的位置からのアクセス
- 通常と異なるUser-Agent
```

**3. 多要素認証（MFA）の強制**
```yaml
authentication_policy:
  mfa:
    required: true
    methods:
      - webauthn
      - totp
    remember_device_days: 30
```

---

## パスワードスプレー攻撃

### 攻撃の仕組み

```
通常のブルートフォース:
1つのアカウント × 多数のパスワード
→ アカウントロックで検知される

パスワードスプレー:
多数のアカウント × 少数のパスワード（1-3個）
→ 各アカウントの試行回数が少ないため検知されにくい

例:
user1 + "Password1!" → 失敗
user2 + "Password1!" → 失敗
user3 + "Password1!" → 成功！
user4 + "Password1!" → 失敗
...
```

### 特徴

```
- 試行間隔を空ける（1時間に1回等）
- IPアドレスを分散
- アカウントロックを回避
- 組織全体を標的
```

### 対策

**1. グローバルレート制限**
```yaml
# アカウント単位ではなく、IP/組織単位で制限
rate_limit:
  per_ip:
    max_attempts_per_hour: 20
  per_organization:
    max_failed_attempts_per_hour: 100
    alert_threshold: 50
```

**2. パスワードスプレー検知**
```java
// 同一パスワードハッシュでの複数アカウント試行を検知
public void detectPasswordSpray() {
    Map<String, Integer> passwordAttempts =
        getPasswordHashAttemptsInLastHour();

    for (Map.Entry<String, Integer> entry : passwordAttempts.entrySet()) {
        if (entry.getValue() > SPRAY_THRESHOLD) {
            alertSecurityTeam("Password spray detected: " +
                entry.getValue() + " accounts targeted");
        }
    }
}
```

**3. CAPTCHA/チャレンジ**
```yaml
authentication_policy:
  captcha:
    enabled: true
    trigger_after_failed_attempts: 3
    type: recaptcha_v3
    min_score: 0.5
```

---

## 防御の多層化

### 防御レイヤー

```
Layer 1: ネットワーク
├── WAF（Web Application Firewall）
├── DDoS Protection
└── IP Reputation

Layer 2: アプリケーション
├── レート制限
├── CAPTCHA
├── アカウントロック
└── プログレッシブディレイ

Layer 3: 認証強化
├── パスワードポリシー
├── 多要素認証
├── リスクベース認証
└── パスキー/WebAuthn

Layer 4: 検知・対応
├── 異常検知
├── セキュリティイベント監視
├── アラート
└── インシデント対応
```

### リスクベース認証

```
リスクスコア計算:
- 新しいデバイス: +30
- 新しいIP: +20
- 異なる国: +40
- 通常と異なる時間: +10
- 過去の不正アクセス履歴: +50

スコアに応じた対応:
0-30: 通常認証
31-60: SMS/Email確認
61-80: MFA必須
81+: アクセス拒否＋管理者通知
```

---

## 実装チェックリスト

### 基本対策
- [ ] パスワードポリシーを設定（12文字以上、複雑性要件）
- [ ] 禁止パスワードリストを導入
- [ ] レート制限を実装
- [ ] アカウントロックアウトを実装
- [ ] HTTPS必須化

### 高度な対策
- [ ] 流出パスワードチェック（Have I Been Pwned）
- [ ] 多要素認証を導入
- [ ] リスクベース認証を導入
- [ ] 異常検知システムを導入
- [ ] パスキー/WebAuthnをサポート

### 監視・対応
- [ ] 認証イベントをログ記録
- [ ] 失敗試行のアラートを設定
- [ ] パスワードスプレー検知を導入
- [ ] インシデント対応手順を文書化

---

## 参考資料

- [OWASP Credential Stuffing Prevention](https://cheatsheetseries.owasp.org/cheatsheets/Credential_Stuffing_Prevention_Cheat_Sheet.html)
- [NIST Digital Identity Guidelines](https://pages.nist.gov/800-63-3/)
- [Have I Been Pwned API](https://haveibeenpwned.com/API/v3)

---

**最終更新**: 2025-12-25
**対象**: セキュリティエンジニア、ID基盤開発者
