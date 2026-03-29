# 復旧パターン

[調査](investigation)で原因が特定できたら（あるいは特定できなくても）、サービスを復旧させます。

**完璧な原因究明より、まず復旧**が原則です。特にユーザー影響が出ている場合、復旧してから落ち着いて原因を調査する方が、結果的に影響時間が短くなります。

---

## 復旧手段の選択

```
原因が特定できた場合:
  │
  ├── リリース起因 → ロールバック
  ├── リソース不足 → スケールアウト / スケールアップ
  ├── DB 起因 → フェイルオーバー / コネクションリセット
  ├── 設定起因 → 設定修正
  └── 外部サービス起因 → サーキットブレーカー / 一時無効化

原因が特定できない場合:
  │
  ├── 直近のリリースあり → まずロールバック
  ├── リリースなし → アプリ再起動
  └── それでもダメ → スケールアウト + エスカレーション
```

---

## パターン別の復旧手順

### 1. ロールバック

**最も速い復旧手段。** 直近のリリースが原因の場合、原因調査より先にロールバックする。

```
Blue-Green の場合（瞬時）:
  aws elbv2 modify-rule --rule-arn $RULE_ARN \
    --actions '[{"Type":"forward","ForwardConfig":{"TargetGroups":[
      {"TargetGroupArn":"'$BLUE_ARN'","Weight":100},
      {"TargetGroupArn":"'$GREEN_ARN'","Weight":0}
    ]}}]'
  → 数秒で復旧

ローリングアップデートの場合:
  aws ecs update-service --cluster idp-cluster \
    --service idp-server \
    --task-definition idp-server:PREVIOUS_VERSION
  → 数分で復旧（旧バージョンのタスクが起動するまで）
```

### 2. スケールアウト

**トラフィック急増やリソース不足が原因の場合。**

```
ECS タスク数を増やす:
  aws ecs update-service --cluster idp-cluster \
    --service idp-server \
    --desired-count 8  # 4 → 8 に増加

Aurora Reader を追加:
  aws rds create-db-instance \
    --db-instance-identifier idp-reader-3 \
    --db-cluster-identifier idp-cluster \
    --db-instance-class db.r6g.large \
    --engine aurora-postgresql

ElastiCache ノードを追加:
  aws elasticache modify-replication-group \
    --replication-group-id idp-redis \
    --apply-immediately
```

### 3. アプリ再起動

**原因不明だが再起動で直る場合（メモリリーク、コネクション枯渇等）。**

```
ECS タスクを強制再起動:
  # 1台ずつ再起動（全台同時はNG）
  aws ecs update-service --cluster idp-cluster \
    --service idp-server \
    --force-new-deployment

  # または特定のタスクだけ停止（新タスクが自動起動）
  aws ecs stop-task --cluster idp-cluster --task $TASK_ARN
```

### 4. DB フェイルオーバー

**Aurora Writer に問題がある場合。**

```
Aurora フェイルオーバー（Writer を Reader に切り替え）:
  aws rds failover-db-cluster \
    --db-cluster-identifier idp-cluster

  → 30秒〜1分で完了
  → エンドポイントは変わらない（アプリ設定変更不要）
  → アプリ側は接続エラーが一時的に発生 → 自動再接続
```

### 5. 外部サービスの一時無効化

**フック実行（Slack/Webhook/Email）の遅延が本体に影響している場合。**

```
対策:
  ① フック設定を無効化（Management API）
     → POST /v1/management/tenants/{id}/security-event-hook-configurations
     → enabled: false に変更

  ② 非同期スレッドプールの設定調整
     → SECURITY_EVENT_MAX_POOL_SIZE を一時的に増加

  ③ 外部サービスのタイムアウトを短縮
     → フック実行の HTTP タイムアウトを 30秒 → 5秒 に
```

---

## 復旧後にやること

```
復旧完了
  │
  ├── 復旧確認
  │   □ ダッシュボードで正常に戻ったことを確認
  │   □ 手動でログイン → トークン発行 → UserInfo のテスト
  │   □ 主要テナントの動作確認
  │
  ├── 報告
  │   □ 「復旧完了」をステークホルダーに報告
  │   □ 暫定対応の内容と恒久対応の予定を伝える
  │
  ├── 監視強化
  │   □ 再発がないか、しばらく監視を続ける
  │   □ 閾値を一時的に厳しくする
  │
  └── 振り返り準備
      □ タイムラインを整理
      □ ポストモーテムの日程調整
```

---

## まとめ

```
復旧の原則:

  1. まず復旧、原因究明はその後
  2. リリース直後なら、ロールバックが最速
  3. 原因不明なら、再起動を試す
  4. 復旧後も監視を続ける
  5. 暫定対応と恒久対応を分けて考える
```

## 次のステップ

- [ポストモーテム](postmortem): 障害の振り返りと再発防止
