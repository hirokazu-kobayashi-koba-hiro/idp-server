# はじめに

## idp-serverとは

idp-server は、身元確認済みのIDを安全に発行・活用できる、新しい世代のアイデンティティ基盤です。

**信頼できるIDを、すべてのサービスへ。** をコンセプトにし、マルチテナントに対応した拡張可能なアイデンティティプロバイダーです。  
OAuth 2.0 / OpenID Connect / CIBA / FAPI / OIDC IDA に準拠し、  
eKYCやVerifiable Credential（VC）を活用した**本人確認済みIDの発行・連携**を可能にします。


## 🛡️ 身元確認を、簡単に。安全に。

電子的本人確認（eKYC）とID連携のプロトコルを標準サポート。  
OpenID for Identity Assurance（OIDC IDA）や Verifiable Credential（VC）に対応し、  
信頼性の高いIDをサービス横断で活用できます。

- OIDC `verified_claims` 対応
- eKYCサービス連携（APIベース）
- VC形式でのID発行・検証機能（オプション）

---

## 🏢 複数テナントでも、スムーズに。

idp-server は、グループ企業や子会社、部門単位や複数環境での分離運用を前提に設計。  
テナントごとのテーマ設定・認証ポリシー・ユーザー管理が可能です。

- テナントごとに完全分離されたデータ構造
- UIテーマや文言カスタマイズ（ブランド対応）
- クライアントごとの柔軟な認可設定

---

## ⚙️ OAuth / OIDC / CIBA / FAPI に標準対応

複数の認可・認証フローをひとつのサーバーで統合管理。  
各種クライアント認証方式や拡張仕様にも準拠しており、金融グレードの認証基盤としても利用可能です。

- 認可コードフロー、CIBA（Push/Ping/Poll）
- FAPI Baseline / Advanced 準拠
- Rich Authorization Requests (RAR)、PAR 対応
- クライアント認証方式：private_key_jwt / mTLS / secret_post など

---

## 🔌 あなたのサービスに、すぐ組み込める。

APIファーストな設計で、既存のWebサービス・モバイルアプリと簡単に統合可能。  

- REST API 対応
- Web / モバイルSDK（React, Android）提供（予定）
- Dockerによる簡単デプロイ

---

