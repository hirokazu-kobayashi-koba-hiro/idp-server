# はじめに

## idp-serverとは

idp-server は、身元確認済みのIDを安全に発行・活用できる、新しい世代のアイデンティティ基盤です。

**信頼できるIDを、すべてのサービスへ。** をコンセプトにし、マルチテナントに対応した拡張可能なアイデンティティプロバイダーです。  
OAuth 2.0 / OpenID Connect / CIBA / FAPI / OIDC IDA に準拠し、  
eKYCやVerifiable Credential（VC）を活用した**本人確認済みIDの発行・連携**を可能にします。

## 特徴

### 🛡️ 身元確認を、簡単に。安全に。

電子的本人確認（eKYC）とID連携のプロトコルを標準サポート。  
OpenID for Identity Assurance（OIDC IDA）や Verifiable Credential（VC）に対応し、  
信頼性の高いIDをサービス横断で活用できます。

- OIDC `verified_claims` 対応
- eKYC サービス連携（REST API / Webhook ベース）
  - 外部 eKYC サービスとの HTTP 連携に対応
  - 詳細: [身元確認申込みガイド](content_05_how-to/how-to-07-identity-verification-application.md)
- Verifiable Credential (VC) 形式での ID 発行・検証機能
  - テナント設定により有効化可能

---

### 🏢 複数テナントでも、スムーズに。

idp-server は、グループ企業や子会社、部門単位や複数環境での分離運用を前提に設計。  
テナントごとのテーマ設定・認証ポリシー・ユーザー管理が可能です。

- テナントごとに完全分離されたデータ構造
- UIテーマや文言カスタマイズ（ブランド対応）
- クライアントごとの柔軟な認可設定

---

### ⚙️ OAuth / OIDC / CIBA / FAPI に標準対応

複数の認可・認証フローをひとつのサーバーで統合管理。  
各種クライアント認証方式や拡張仕様にも準拠しており、金融グレードの認証基盤としても利用可能です。

- 認可コードフロー、CIBA（Push/Ping/Poll）
- FAPI Baseline / Advanced 準拠
- Rich Authorization Requests (RAR)、PAR 対応
- クライアント認証方式：private_key_jwt / mTLS / secret_post など

---

### 🔌 シームレスなサービス連携

APIファーストな設計で、既存のWebサービス・モバイルアプリと簡単に統合可能。  

- REST API 対応
- Web / モバイルSDK（Android, iOS）提供（予定）

---

## 想定利用ケース

### 🏦 金融機関：オンライン口座開設・投資口座の本人確認

eKYCを通じた**本人確認済みID**を発行し、以後のログインやサービス連携時に再確認不要な体験を提供。  
複数の証券・銀行サービス間でのID連携や本人情報の再利用にも活用可能。

- eKYCプロバイダーとのAPI連携（例：スマホでの本人確認）
- OIDC IDAやVCで、信頼性の高い情報を連携
- 認証履歴の管理やセキュリティイベントのフックも対応

---

### 🏢 企業グループ：グループ会社間での共通ID管理

持株会社配下の各子会社ごとにテナントを分離しつつ、  
**共通認証基盤としてidp-serverを一元運用**。個別ブランド・ポリシーにも対応可能。

- テナント単位でテーマやMFAポリシーを設定
- 共通IDを使って社内ポータル・SaaSにログイン
- OAuthクライアントやSCIM連携も予定対応

---

### 🧾 行政・公的機関：デジタル住民サービスのID基盤

マイナンバー連携やeKYCを通じて身元を確認したIDをもとに、  
さまざまな住民向けサービスのID連携に利用。Verifiable Credentialによる証明書発行も対応可能。

- Verifiable Credential による証明書交付（例：居住証明）
- OpenID Connectによる標準的なID連携
- 身元保証・プライバシー制御（OIDC IDA準拠）

---

### 🛍️ Webサービス / SaaS：信頼できるユーザーIDの導入

SaaSや会員制Webサービスに、**信頼性の高いID認証を簡単に導入**可能。  
個人ユーザーのなりすまし防止や、年齢確認・職業確認の仕組みとして活用。

- WebAuthn / Passkey によるパスワードレス認証
- OIDC verified_claims で年齢や所属を連携
- 柔軟なスコープ・claims制御で必要情報のみ提供

---

### 👥 プラットフォーマー：外部サービスへのID提供

自社ユーザーのIDを**外部パートナーに提供**する「IDプロバイダー」としての活用。  
CIBAやPush通知によるシームレスな認証も可能。

- OAuth / OIDC を通じて他社サービスと連携
- CIBA（Backchannel Authentication）でログイン
- 契約者ベースでの柔軟な認可ポリシー制御
