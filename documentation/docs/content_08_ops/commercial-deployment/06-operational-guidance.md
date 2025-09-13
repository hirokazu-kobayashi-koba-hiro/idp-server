# 運用ガイダンス

idp-server の商用環境における日常的な運用管理、監視、バックアップ、インシデント対応、セキュリティ監査について説明します。

---

## 📊 日常運用・監視

### システム監視項目

#### アプリケーション監視
```bash
# 日次ヘルスチェック
#!/bin/bash
echo "=== Daily Health Check $(date) ===" >> /var/log/idp-health-check.log

# アプリケーションヘルス
curl -s "$SERVER_URL/actuator/health" | jq '.status' >> /var/log/idp-health-check.log

# データベース接続状況
curl -s "$SERVER_URL/actuator/health/db" | jq '.status' >> /var/log/idp-health-check.log

# Redis 接続状況
curl -s "$SERVER_URL/actuator/health/redis" | jq '.status' >> /var/log/idp-health-check.log

# JVM メモリ使用率
curl -s "$SERVER_URL/actuator/metrics/jvm.memory.used" | \
  jq '.measurements[0].value' >> /var/log/idp-health-check.log
```

#### インフラ監視 (CloudWatch)
```bash
# CPU 使用率取得
aws cloudwatch get-metric-statistics \
  --namespace "AWS/ECS" \
  --metric-name "CPUUtilization" \
  --dimensions Name=ServiceName,Value=idp-server-service \
  --start-time $(date -d '1 hour ago' --iso-8601) \
  --end-time $(date --iso-8601) \
  --period 300 \
  --statistics Average

# メモリ使用率取得
aws cloudwatch get-metric-statistics \
  --namespace "AWS/ECS" \
  --metric-name "MemoryUtilization" \
  --dimensions Name=ServiceName,Value=idp-server-service \
  --start-time $(date -d '1 hour ago' --iso-8601) \
  --end-time $(date --iso-8601) \
  --period 300 \
  --statistics Average
```

### パフォーマンス監視

#### レスポンス時間監視
```bash
# 認証エンドポイントレスポンス時間
curl -w "@curl-format.txt" -o /dev/null -s \
  -X POST "$SERVER_URL/$TENANT_ID/oauth/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=$CLIENT_ID&client_secret=$CLIENT_SECRET"

# curl-format.txt:
#     time_namelookup:  %{time_namelookup}\n
#      time_connect:  %{time_connect}\n
#   time_appconnect:  %{time_appconnect}\n
#  time_pretransfer:  %{time_pretransfer}\n
#     time_redirect:  %{time_redirect}\n
#time_starttransfer:  %{time_starttransfer}\n
#                   ----------\n
#        time_total:  %{time_total}\n
```

#### データベース性能監視
```bash
# データベース接続プール監視
curl -s "$SERVER_URL/actuator/metrics/hikaricp.connections.active" | \
  jq '.measurements[0].value'

# スロークエリ監視
aws logs filter-log-events \
  --log-group-name "/aws/rds/instance/idp-server-postgresql/postgresql" \
  --filter-pattern "duration" \
  --start-time $(date -d '1 hour ago' +%s)000
```

### セキュリティ監視

#### 認証失敗監視
```bash
# 認証失敗回数集計
curl -X GET "$SERVER_URL/v1/admin/tenants/$TENANT_ID/audit-logs" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -G -d "event_type=authentication_failed" \
  -d "start_time=$(date -d '1 hour ago' --iso-8601)" \
  -d "end_time=$(date --iso-8601)" | \
  jq '.total_count'

# IP別異常アクセス検出
curl -X GET "$SERVER_URL/v1/admin/security-events/suspicious-activities" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -G -d "time_range=1h" | jq
```

#### 異常な管理者活動監視
```bash
# 管理者操作監視
curl -X GET "$SERVER_URL/v1/admin/tenants/$TENANT_ID/audit-logs" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -G -d "event_type=admin_action" \
  -d "start_time=$(date -d '24 hours ago' --iso-8601)" | \
  jq '.events[] | select(.outcome == "failure")'
```

---

## 🔧 日常メンテナンス

### ログローテーション・清理

```bash
#!/bin/bash
# log-cleanup.sh - 日次ログ清理

# CloudWatch ログ保持期間確認
aws logs describe-log-groups \
  --log-group-name-prefix "/ecs/idp-server" \
  --query 'logGroups[*].{Name:logGroupName,RetentionDays:retentionInDays}'

# 古いログファイル削除 (ローカル)
find /var/log/idp-server -name "*.log" -mtime +30 -delete

# 監査ログアーカイブ (90日経過)
curl -X POST "$SERVER_URL/v1/admin/audit-logs/archive" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -H "Content-Type: application/json" \
  --data '{
    "retention_days": 90,
    "archive_location": "s3://idp-audit-archive/",
    "compression": true
  }'
```

### データベースメンテナンス

#### 統計情報更新・インデックス再構築
```bash
# PostgreSQL 統計情報更新
psql -h $DB_HOST -U $DB_USER -d idpserver -c "ANALYZE;"

# インデックス再構築
psql -h $DB_HOST -U $DB_USER -d idpserver -c "REINDEX INDEX CONCURRENTLY idx_users_tenant_id;"

# 接続数監視
psql -h $DB_HOST -U $DB_USER -d idpserver -c "
  SELECT
    count(*) as total_connections,
    count(*) filter (where state = 'active') as active_connections,
    count(*) filter (where state = 'idle') as idle_connections
  FROM pg_stat_activity
  WHERE datname = 'idpserver';"
```

#### バキューム・容量監視
```bash
# 定期バキューム (週次)
psql -h $DB_HOST -U $DB_USER -d idpserver -c "VACUUM ANALYZE;"

# データベース容量監視
psql -h $DB_HOST -U $DB_USER -d idpserver -c "
  SELECT
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as size
  FROM pg_tables
  WHERE schemaname = 'public'
  ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC
  LIMIT 10;"
```

### Redis メンテナンス

```bash
# Redis メモリ使用量確認
redis-cli -h $REDIS_HOST -p $REDIS_PORT -a $REDIS_PASSWORD INFO memory

# キー有効期限確認
redis-cli -h $REDIS_HOST -p $REDIS_PORT -a $REDIS_PASSWORD \
  --scan --pattern "session:*" | head -10 | \
  xargs -I {} redis-cli -h $REDIS_HOST -p $REDIS_PORT -a $REDIS_PASSWORD TTL {}

# 不要キー削除 (期限切れセッション等)
redis-cli -h $REDIS_HOST -p $REDIS_PORT -a $REDIS_PASSWORD \
  --scan --pattern "expired:*" | \
  xargs -r redis-cli -h $REDIS_HOST -p $REDIS_PORT -a $REDIS_PASSWORD DEL
```

---

## 💾 バックアップ・復旧

### データベースバックアップ

#### 自動バックアップ設定 (RDS)
```bash
# RDS 自動バックアップ設定確認
aws rds describe-db-instances \
  --db-instance-identifier idp-server-postgresql \
  --query 'DBInstances[0].{BackupRetentionPeriod:BackupRetentionPeriod,PreferredBackupWindow:PreferredBackupWindow}'

# 手動スナップショット作成
aws rds create-db-snapshot \
  --db-instance-identifier idp-server-postgresql \
  --db-snapshot-identifier idp-server-manual-$(date +%Y%m%d-%H%M%S)
```

#### 論理バックアップ (pg_dump)
```bash
#!/bin/bash
# database-backup.sh - 論理バックアップスクリプト

BACKUP_DATE=$(date +%Y%m%d-%H%M%S)
BACKUP_FILE="idp-server-backup-$BACKUP_DATE.sql"

# pg_dump 実行
pg_dump -h $DB_HOST -U $DB_USER -d idpserver \
  --format=custom \
  --compress=9 \
  --no-privileges \
  --no-owner \
  --file=$BACKUP_FILE

# S3 アップロード
aws s3 cp $BACKUP_FILE s3://idp-server-backups/database/

# ローカルバックアップファイル削除 (7日経過)
find /backup -name "idp-server-backup-*.sql" -mtime +7 -delete
```

#### ポイントインタイムリカバリー (PITR)
```bash
# 特定時点への復旧
aws rds restore-db-instance-to-point-in-time \
  --source-db-instance-identifier idp-server-postgresql \
  --target-db-instance-identifier idp-server-restore-$(date +%Y%m%d) \
  --restore-time "2024-01-15T10:30:00.000Z" \
  --db-subnet-group-name idp-server-db-subnet-group
```

### 設定バックアップ

#### テナント・ユーザー設定バックアップ
```bash
#!/bin/bash
# config-backup.sh - 設定バックアップスクリプト

BACKUP_DATE=$(date +%Y%m%d-%H%M%S)
BACKUP_DIR="/backup/config-$BACKUP_DATE"
mkdir -p $BACKUP_DIR

# 全テナント設定エクスポート
curl -X GET "$SERVER_URL/v1/admin/tenants?export=true" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -o "$BACKUP_DIR/tenants.json"

# 全クライアント設定エクスポート
for TENANT_ID in $(jq -r '.tenants[].identifier' "$BACKUP_DIR/tenants.json"); do
  curl -X GET "$SERVER_URL/v1/admin/tenants/$TENANT_ID/clients?export=true" \
    -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
    -o "$BACKUP_DIR/clients-$TENANT_ID.json"
done

# 設定バックアップアーカイブ
tar -czf "config-backup-$BACKUP_DATE.tar.gz" -C /backup config-$BACKUP_DATE

# S3 アップロード
aws s3 cp "config-backup-$BACKUP_DATE.tar.gz" s3://idp-server-backups/config/
```

### 復旧手順

#### データベース復旧
```bash
#!/bin/bash
# database-restore.sh - データベース復旧スクリプト

BACKUP_FILE=$1

if [ -z "$BACKUP_FILE" ]; then
  echo "Usage: $0 <backup_file>"
  exit 1
fi

# S3 からバックアップファイルダウンロード
aws s3 cp "s3://idp-server-backups/database/$BACKUP_FILE" ./

# データベース復旧
pg_restore -h $DB_HOST -U $DB_USER -d idpserver \
  --clean \
  --no-privileges \
  --no-owner \
  --verbose \
  $BACKUP_FILE
```

#### 設定復旧
```bash
#!/bin/bash
# config-restore.sh - 設定復旧スクリプト

CONFIG_BACKUP=$1

# バックアップファイル展開
tar -xzf $CONFIG_BACKUP

# テナント設定復旧
curl -X POST "$SERVER_URL/v1/admin/tenants/import" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -H "Content-Type: application/json" \
  --data @./tenants.json

# クライアント設定復旧
for CLIENT_FILE in clients-*.json; do
  TENANT_ID=$(echo $CLIENT_FILE | sed 's/clients-\(.*\)\.json/\1/')
  curl -X POST "$SERVER_URL/v1/admin/tenants/$TENANT_ID/clients/import" \
    -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
    -H "Content-Type: application/json" \
    --data @./$CLIENT_FILE
done
```

---

## 🚨 インシデント対応

### セキュリティインシデント対応

#### 不正アクセス検出時の対応
```bash
#!/bin/bash
# security-incident-response.sh

INCIDENT_ID=$1
SUSPICIOUS_IP=$2

echo "=== Security Incident Response: $INCIDENT_ID ===" | tee -a /var/log/security-incidents.log

# 1. 該当IPアドレスのブロック (WAF)
aws wafv2 update-ip-set \
  --scope REGIONAL \
  --id $WAF_IP_SET_ID \
  --addresses $SUSPICIOUS_IP/32

# 2. 該当IPからの認証試行を監査ログで確認
curl -X GET "$SERVER_URL/v1/admin/audit-logs" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -G -d "ip_address=$SUSPICIOUS_IP" \
  -d "start_time=$(date -d '24 hours ago' --iso-8601)" | \
  jq '.events[] | {timestamp, event_type, outcome, user_id}' | \
  tee -a "/var/log/incident-$INCIDENT_ID-audit.log"

# 3. 影響を受けた可能性のあるユーザーセッション無効化
curl -X POST "$SERVER_URL/v1/admin/security/revoke-sessions" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -H "Content-Type: application/json" \
  --data "{
    \"criteria\": {
      \"ip_address\": \"$SUSPICIOUS_IP\",
      \"time_range\": \"24h\"
    }
  }"

# 4. Slack 通知
curl -X POST $SLACK_WEBHOOK_URL \
  -H "Content-Type: application/json" \
  --data "{
    \"text\": \"🚨 Security Incident Alert\",
    \"attachments\": [{
      \"color\": \"danger\",
      \"fields\": [
        {\"title\": \"Incident ID\", \"value\": \"$INCIDENT_ID\", \"short\": true},
        {\"title\": \"Suspicious IP\", \"value\": \"$SUSPICIOUS_IP\", \"short\": true},
        {\"title\": \"Action Taken\", \"value\": \"IP blocked, sessions revoked\", \"short\": false}
      ]
    }]
  }"
```

#### データ侵害インシデント対応
```bash
#!/bin/bash
# data-breach-response.sh

BREACH_ID=$1
AFFECTED_TENANT=$2

echo "=== Data Breach Response: $BREACH_ID ===" | tee -a /var/log/data-breaches.log

# 1. 影響範囲調査
curl -X GET "$SERVER_URL/v1/admin/tenants/$AFFECTED_TENANT/users" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -G -d "include_pii=true" | \
  jq '.users | length' | \
  tee -a "/var/log/breach-$BREACH_ID-impact.log"

# 2. 全ユーザーのパスワードリセット要求
curl -X POST "$SERVER_URL/v1/admin/tenants/$AFFECTED_TENANT/security/force-password-reset" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -H "Content-Type: application/json" \
  --data '{
    "reason": "security_breach",
    "notify_users": true
  }'

# 3. 全アクセストークン・リフレッシュトークン無効化
curl -X POST "$SERVER_URL/v1/admin/tenants/$AFFECTED_TENANT/security/revoke-all-tokens" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -H "Content-Type: application/json"

# 4. GDPR/CCPA 当局への報告準備 (72時間以内)
echo "Breach detected at $(date)" | \
  tee -a "/var/log/breach-$BREACH_ID-regulatory-report.log"
```

### システム障害対応

#### サービス停止時の対応
```bash
#!/bin/bash
# service-outage-response.sh

OUTAGE_ID=$1

echo "=== Service Outage Response: $OUTAGE_ID ===" | tee -a /var/log/outages.log

# 1. システム状態確認
curl -s -o /dev/null -w "%{http_code}" "$SERVER_URL/actuator/health" | \
  tee -a "/var/log/outage-$OUTAGE_ID-status.log"

# 2. ECS サービススケーリング (緊急対応)
aws ecs update-service \
  --cluster idp-server-cluster \
  --service idp-server-service \
  --desired-count 4

# 3. データベース接続確認
psql -h $DB_HOST -U $DB_USER -d idpserver -c "SELECT 1;" 2>&1 | \
  tee -a "/var/log/outage-$OUTAGE_ID-db-status.log"

# 4. 外部ステータスページ更新
curl -X POST "https://api.statuspage.io/v1/pages/$STATUSPAGE_ID/incidents" \
  -H "Authorization: OAuth $STATUSPAGE_TOKEN" \
  -H "Content-Type: application/json" \
  --data "{
    \"incident\": {
      \"name\": \"IdP Service Degradation\",
      \"status\": \"investigating\",
      \"impact_override\": \"major\"
    }
  }"
```

### 復旧確認・事後対応

```bash
#!/bin/bash
# post-incident-verification.sh

INCIDENT_ID=$1

echo "=== Post-Incident Verification: $INCIDENT_ID ===" | tee -a /var/log/post-incident.log

# 1. 全機能の動作確認
curl -X POST "$SERVER_URL/$TENANT_ID/oauth/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=$CLIENT_ID&client_secret=$CLIENT_SECRET" | \
  jq '.access_token' | \
  tee -a "/var/log/incident-$INCIDENT_ID-recovery-test.log"

# 2. パフォーマンス確認
ab -n 100 -c 10 "$SERVER_URL/actuator/health" 2>&1 | \
  grep "Requests per second" | \
  tee -a "/var/log/incident-$INCIDENT_ID-performance.log"

# 3. 事後報告書生成
cat > "/var/log/incident-$INCIDENT_ID-report.md" << EOF
# Incident Report: $INCIDENT_ID

## Timeline
- Detected: $(date -d '2 hours ago')
- Response Started: $(date -d '1.5 hours ago')
- Resolved: $(date)

## Impact
- Affected Services: Identity Provider
- Duration: 2 hours
- Users Affected: TBD

## Root Cause
[To be investigated]

## Action Items
- [ ] Update monitoring alerts
- [ ] Improve response procedures
- [ ] Schedule post-mortem meeting
EOF
```

---

## 📋 監査・コンプライアンス

### 定期監査準備

#### SOC 2 監査対応
```bash
#!/bin/bash
# soc2-audit-preparation.sh

AUDIT_PERIOD_START="2024-01-01"
AUDIT_PERIOD_END="2024-12-31"

echo "=== SOC 2 Audit Preparation ===" | tee audit-soc2.log

# 1. アクセス制御監査証跡収集
curl -X GET "$SERVER_URL/v1/admin/audit-logs" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -G -d "event_type=access_control" \
  -d "start_time=${AUDIT_PERIOD_START}T00:00:00Z" \
  -d "end_time=${AUDIT_PERIOD_END}T23:59:59Z" \
  -d "export=csv" > "soc2-access-control-logs.csv"

# 2. 設定変更履歴収集
curl -X GET "$SERVER_URL/v1/admin/audit-logs" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -G -d "event_type=configuration_change" \
  -d "start_time=${AUDIT_PERIOD_START}T00:00:00Z" \
  -d "end_time=${AUDIT_PERIOD_END}T23:59:59Z" \
  -d "export=csv" > "soc2-config-changes.csv"

# 3. セキュリティインシデント報告書
grep "Security Incident" /var/log/security-incidents.log | \
  awk -v start="$AUDIT_PERIOD_START" -v end="$AUDIT_PERIOD_END" \
  '$1 >= start && $1 <= end' > "soc2-security-incidents.log"
```

#### GDPR コンプライアンス監査
```bash
#!/bin/bash
# gdpr-compliance-audit.sh

echo "=== GDPR Compliance Audit ===" | tee audit-gdpr.log

# 1. データ削除要求処理状況
curl -X GET "$SERVER_URL/v1/admin/gdpr/deletion-requests" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -G -d "status=all" \
  -d "export=csv" > "gdpr-deletion-requests.csv"

# 2. データエクスポート要求処理状況
curl -X GET "$SERVER_URL/v1/admin/gdpr/export-requests" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -G -d "status=all" \
  -d "export=csv" > "gdpr-export-requests.csv"

# 3. 同意管理履歴
curl -X GET "$SERVER_URL/v1/admin/audit-logs" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -G -d "event_type=consent_change" \
  -d "export=csv" > "gdpr-consent-history.csv"
```

### セキュリティ監査

#### 脆弱性スキャン
```bash
#!/bin/bash
# vulnerability-scan.sh

echo "=== Vulnerability Scan ===" | tee security-scan.log

# 1. Dependency Check (OWASP)
docker run --rm -v $(pwd):/src owasp/dependency-check:latest \
  --scan /src --format HTML --out /src/dependency-check-report.html

# 2. Container Security Scan
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
  aquasec/trivy:latest image idp-server:latest > container-security-scan.txt

# 3. Network Security Scan
nmap -sV -O $SERVER_URL > network-scan.txt

# 4. SSL/TLS 設定確認
openssl s_client -connect $(echo $SERVER_URL | cut -d'/' -f3):443 \
  -servername $(echo $SERVER_URL | cut -d'/' -f3) < /dev/null 2>/dev/null | \
  openssl x509 -text > ssl-certificate-info.txt
```

#### ペネトレーションテスト準備
```bash
#!/bin/bash
# pentest-preparation.sh

echo "=== Penetration Test Preparation ===" | tee pentest-prep.log

# 1. テスト環境設定確認
curl -X GET "$SERVER_URL/actuator/info" | \
  jq '.git.branch, .build.version' | \
  tee -a pentest-prep.log

# 2. 認証境界確認
curl -X GET "$SERVER_URL/$TENANT_ID/.well-known/openid_configuration" | \
  jq '.authorization_endpoint, .token_endpoint, .userinfo_endpoint' | \
  tee -a pentest-endpoints.log

# 3. レート制限設定確認
for i in {1..20}; do
  curl -s -o /dev/null -w "%{http_code} " \
    -X POST "$SERVER_URL/$TENANT_ID/oauth/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=client_credentials&client_id=invalid&client_secret=invalid"
done
echo | tee -a pentest-rate-limiting.log
```

---

## 📈 容量計画・スケーリング

### 使用量監視・分析

```bash
#!/bin/bash
# usage-analysis.sh

ANALYSIS_DATE=$(date +%Y%m%d)

echo "=== Usage Analysis: $ANALYSIS_DATE ===" | tee usage-analysis-$ANALYSIS_DATE.log

# 1. 認証リクエスト数分析
curl -X GET "$SERVER_URL/v1/admin/metrics/authentication-requests" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -G -d "period=30d" \
  -d "group_by=day" | \
  jq '.data[].count' | \
  awk '{sum+=$1} END {print "Average daily authentications:", sum/NR}' | \
  tee -a usage-analysis-$ANALYSIS_DATE.log

# 2. アクティブユーザー数分析
curl -X GET "$SERVER_URL/v1/admin/metrics/active-users" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -G -d "period=30d" | \
  jq '.monthly_active_users' | \
  tee -a usage-analysis-$ANALYSIS_DATE.log

# 3. データベース容量増加率
psql -h $DB_HOST -U $DB_USER -d idpserver -t -c "
  SELECT
    pg_size_pretty(pg_database_size('idpserver')) as current_size,
    'Growth rate analysis needed' as note;
" | tee -a usage-analysis-$ANALYSIS_DATE.log
```

### 自動スケーリング設定

```bash
# ECS Auto Scaling 設定
aws application-autoscaling register-scalable-target \
  --service-namespace ecs \
  --scalable-dimension ecs:service:DesiredCount \
  --resource-id service/idp-server-cluster/idp-server-service \
  --min-capacity 2 \
  --max-capacity 10

aws application-autoscaling put-scaling-policy \
  --service-namespace ecs \
  --scalable-dimension ecs:service:DesiredCount \
  --resource-id service/idp-server-cluster/idp-server-service \
  --policy-name idp-server-cpu-scaling \
  --policy-type TargetTrackingScaling \
  --target-tracking-scaling-policy-configuration '{
    "TargetValue": 70.0,
    "PredefinedMetricSpecification": {
      "PredefinedMetricType": "ECSServiceAverageCPUUtilization"
    },
    "ScaleOutCooldown": 300,
    "ScaleInCooldown": 300
  }'
```

---

## ✅ 運用チェックリスト

### 日次運用
- [ ] システムヘルスチェック実行
- [ ] エラーログ・アラート確認
- [ ] パフォーマンス指標確認
- [ ] セキュリティイベント確認
- [ ] バックアップ実行状況確認

### 週次運用
- [ ] データベースメンテナンス実行
- [ ] 容量・使用量分析
- [ ] セキュリティパッチ適用検討
- [ ] 監査ログレビュー
- [ ] インシデント対応状況確認

### 月次運用
- [ ] 設定バックアップ実行
- [ ] 災害復旧テスト実行
- [ ] 容量計画見直し
- [ ] セキュリティ監査実施
- [ ] パフォーマンス改善検討

### 四半期運用
- [ ] 脆弱性スキャン実行
- [ ] ペネトレーションテスト実施
- [ ] コンプライアンス監査対応
- [ ] 運用手順見直し
- [ ] 事業継続計画更新

### 年次運用
- [ ] 包括的セキュリティ監査
- [ ] 災害復旧計画更新
- [ ] 法令・規制対応確認
- [ ] 運用コスト最適化
- [ ] 技術スタック更新検討

---

## 🔗 関連ドキュメント

- [前提条件](./01-prerequisites.md)
- [環境変数・セキュリティパラメータ](./02-environment-variables.md)
- [AWS インフラ構築](./03-aws-infrastructure.md)
- [初期設定・ユーザー・ロール](./04-initial-configuration.md)
- [検証・テストチェックリスト](./05-verification-checklist.md)