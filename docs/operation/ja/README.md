# idp-server 運用ドキュメント

## 📋 ドキュメント一覧

### 🔍 ログ・セキュリティイベント分析
- **[セキュリティイベントログ 運用リファレンス](./security-events-log-reference.md)**
  - 108種類の全セキュリティイベントの詳細説明
  - ユーザー問い合わせ調査の完全ガイド
  - プライバシー・コンプライアンス考慮事項

- **[セキュリティイベント クイックリファレンス](./security-events-quick-reference.md)**
  - 最頻出サポートケースの即座対応ガイド
  - すぐに使えるコマンド例とログ検索方法
  - 緊急時対応手順

- **[セキュリティイベント Tags リファレンス](./security-event-tags-reference.md)**
  - ログ検索・分析用の自動生成タグ仕様
  - カテゴリ別・成功失敗別の検索方法
  - ダッシュボード・アラート設定例

### 📊 監視・ダッシュボード
- **[ログ監視ダッシュボード設定ガイド](./log-monitoring-dashboard-setup.md)**
  - Grafana + Loki, ELK Stack, Splunk対応
  - アラート設定テンプレート
  - KPI・メトリクス定義

## 🚨 緊急時対応

### 最初に確認すべきコマンド
```bash
# システム全体の健全性チェック
grep "$(date '+%Y-%m-%d')" /var/log/idp-server.log | grep -E "_(failure|error)" | wc -l

# 特定ユーザーの問題調査
grep "user_id:USER123" /var/log/idp-server.log | tail -10

# 過去1時間の認証失敗率
TOTAL=$(grep "$(date -d '1 hour ago' '+%Y-%m-%d %H'):" /var/log/idp-server.log | wc -l)
FAILED=$(grep "$(date -d '1 hour ago' '+%Y-%m-%d %H'):" /var/log/idp-server.log | grep "password_failure" | wc -l)
echo "認証失敗率: $(($FAILED * 100 / $TOTAL))%"
```

## 📞 サポート対応フローチャート

### ユーザーからの問い合わせ対応
```
1. 基本情報収集
   ├─ ユーザーID
   ├─ 発生時刻
   ├─ エラーメッセージ
   └─ 使用環境（ブラウザ・デバイス）

2. ログ確認
   └─ grep "user_id:${USER_ID}" /var/log/idp-server.log

3. 問題分類
   ├─ password_failure → パスワード問題
   ├─ *_registration_failure → MFA問題  
   ├─ oauth_*_failure → 連携問題
   └─ federation_failure → 外部IdP問題

4. 解決案内
   ├─ 設定確認依頼
   ├─ 再試行案内
   ├─ 管理者エスカレーション
   └─ 開発チーム報告
```

## 🎯 主要なKPI

### 日次監視項目
- **認証成功率**: 95%以上が正常
- **エラー率**: 5%以下が正常
- **新規ユーザー登録数**: トレンド監視
- **MFA登録率**: 月次トレンド

### 即座にエスカレーションすべきアラート
- 🔴 **Critical**: システム全体のエラー率が10%超過
- 🔴 **Critical**: 単一IPからの100回以上のログイン失敗
- 🟡 **Warning**: 特定サービスのエラー率が20%超過
- 🟡 **Warning**: MFA登録失敗の急増

## 📊 ダッシュボード推奨構成

### 運用チーム用ダッシュボード
1. **システム概要**
   - 現在のエラー率
   - アクティブユーザー数
   - トランザクション数/分

2. **問題検知**
   - 最新の失敗イベント
   - 不審なIPアドレス
   - アカウントロック状況

3. **トレンド分析**
   - 日次/週次の成功率推移
   - ユーザー登録数推移
   - 連携サービス利用状況

## 🔧 よくあるトラブルシューティング

### Q: ユーザーがログインできない
**A**: 以下の順番で確認
1. `password_failure` → パスワード確認依頼
2. `user_disabled` → 管理者による有効化が必要
3. `user_lock` → アカウント解除手続き

### Q: MFA設定ができない
**A**: デバイス互換性と環境を確認
1. `device_not_supported` → 対応デバイス案内
2. `registration_timeout` → ネットワーク環境確認
3. `invalid_signature` → デバイス再登録案内

### Q: OAuth連携でエラーが発生
**A**: 設定とスコープを確認
1. `invalid_scope` → クライアント設定確認
2. `redirect_uri_mismatch` → 登録情報確認  
3. `provider_unavailable` → 外部サービス状況確認

## 📚 関連ドキュメント

### 開発チーム向け
- [APIリファレンス](../../api/)
- [設定管理ガイド](../../configuration/)

### セキュリティチーム向け
- [セキュリティ設定ガイド](../../security/)
- [コンプライアンス報告書](../../compliance/)

### 管理者向け
- [ユーザー管理ガイド](../../admin/user-management/)
- [クライアント管理ガイド](../../admin/client-management/)

## 📱 連絡先

### 緊急時エスカレーション
- **P1 (システム全停止)**: +81-90-1234-5678 (24時間対応)
- **P2 (機能障害)**: ops-emergency@company.com
- **P3 (部分的問題)**: ops-support@company.com

### チーム連絡先
- **運用チーム**: ops@company.com
- **セキュリティチーム**: security@company.com
- **開発チーム**: dev@company.com
- **サポートチーム**: support@company.com

## 📅 定期メンテナンス

### 日次作業 (自動化推奨)
- [ ] ログローテーション確認
- [ ] ディスク容量チェック
- [ ] エラー率レポート生成

### 週次作業
- [ ] トレンド分析レポート
- [ ] アラート設定見直し
- [ ] 問い合わせパターン分析

### 月次作業
- [ ] ダッシュボード設定更新
- [ ] 運用手順見直し
- [ ] チーム向けトレーニング実施

---

**ドキュメント更新**: 2025年9月10日  
**次回見直し**: 2025年12月10日  
**メンテナー**: 運用チーム ops@company.com