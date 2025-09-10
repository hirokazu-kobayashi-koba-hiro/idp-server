# ログ監視ダッシュボード設定ガイド

## 概要
本ドキュメントでは、idp-serverのセキュリティイベントと運用メトリクスを追跡するための監視ダッシュボード設定例を提供します。

## サポート対象監視プラットフォーム

### 1. Grafana + Loki 設定

#### Loki設定 (`loki.yml`)
```yaml
auth_enabled: false

server:
  http_listen_port: 3100

ingester:
  lifecycler:
    address: 127.0.0.1
    ring:
      kvstore:
        store: inmemory
      replication_factor: 1

schema_config:
  configs:
    - from: 2020-10-24
      store: boltdb-shipper
      object_store: filesystem
      schema: v11
      index:
        prefix: index_
        period: 24h

storage_config:
  boltdb_shipper:
    active_index_directory: /loki/boltdb-shipper-active
    cache_location: /loki/boltdb-shipper-cache
  filesystem:
    directory: /loki/chunks

limits_config:
  enforce_metric_name: false
  reject_old_samples: true
  reject_old_samples_max_age: 168h

chunk_store_config:
  max_look_back_period: 0s

table_manager:
  retention_deletes_enabled: false
  retention_period: 0s
```

#### Promtail設定 (`promtail.yml`)
```yaml
server:
  http_listen_port: 9080
  grpc_listen_port: 0

positions:
  filename: /tmp/positions.yaml

clients:
  - url: http://localhost:3100/loki/api/v1/push

scrape_configs:
  - job_name: idp-server
    static_configs:
      - targets:
          - localhost
        labels:
          job: idp-server
          __path__: /var/log/idp-server/*.log
    pipeline_stages:
      - regex:
          expression: '^(?P<timestamp>\S+ \S+) (?P<level>\w+) \[(?P<logger>[^\]]+)\] (?P<event_type>\w+): (?P<message>.*)'
      - labels:
          level:
          event_type:
          logger:
```

#### Grafanaダッシュボード設定例
```json
{
  "dashboard": {
    "id": null,
    "title": "IDPサーバー セキュリティイベント",
    "tags": ["idp-server", "security"],
    "timezone": "Asia/Tokyo",
    "panels": [
      {
        "id": 1,
        "title": "認証成功率",
        "type": "stat",
        "targets": [
          {
            "expr": "sum(rate({job=\"idp-server\"} |= \"password_success\" [5m]))",
            "legendFormat": "成功率"
          }
        ]
      },
      {
        "id": 2, 
        "title": "ログイン失敗回数",
        "type": "graph",
        "targets": [
          {
            "expr": "sum by (level) (rate({job=\"idp-server\"} |= \"password_failure\" [5m]))",
            "legendFormat": "ログイン失敗"
          }
        ]
      },
      {
        "id": 3,
        "title": "MFA登録イベント",
        "type": "table",
        "targets": [
          {
            "expr": "{job=\"idp-server\"} |= \"fido_uaf_registration\" or \"webauthn_registration\"",
            "legendFormat": ""
          }
        ]
      }
    ],
    "time": {
      "from": "now-6h",
      "to": "now"
    },
    "refresh": "30s"
  }
}
```

### 2. ELK Stack (Elasticsearch + Logstash + Kibana)

#### Logstash設定 (`idp-server.conf`)
```ruby
input {
  file {
    path => "/var/log/idp-server/*.log"
    start_position => "beginning"
    codec => "json"
  }
}

filter {
  grok {
    match => { 
      "message" => "%{TIMESTAMP_ISO8601:timestamp} %{LOGLEVEL:level} \[%{DATA:logger}\] %{WORD:event_type}: %{GREEDYDATA:event_data}"
    }
  }
  
  kv {
    source => "event_data"
    field_split => ", "
    value_split => "="
  }
  
  date {
    match => [ "timestamp", "yyyy-MM-dd HH:mm:ss.SSS" ]
  }
  
  # 失敗イベントにタグ付け
  if [event_type] =~ /.*_failure/ {
    mutate {
      add_tag => ["失敗"]
    }
  }
  
  # 成功イベントにタグ付け
  if [event_type] =~ /.*_success/ {
    mutate {
      add_tag => ["成功"]
    }
  }
}

output {
  elasticsearch {
    hosts => ["localhost:9200"]
    index => "idp-server-logs-%{+YYYY.MM.dd}"
  }
}
```

#### Kibanaダッシュボードクエリ

**認証失敗ダッシュボード**
```json
{
  "query": {
    "bool": {
      "must": [
        {"match": {"event_type": "password_failure"}},
        {"range": {"@timestamp": {"gte": "now-1h"}}}
      ]
    }
  },
  "aggs": {
    "時間別失敗数": {
      "date_histogram": {
        "field": "@timestamp",
        "calendar_interval": "5m"
      }
    },
    "失敗の多いIPアドレス": {
      "terms": {
        "field": "ip_address.keyword",
        "size": 10
      }
    }
  }
}
```

### 3. Splunk設定

#### Splunk入力設定 (`inputs.conf`)
```ini
[monitor:///var/log/idp-server/*.log]
disabled = false
index = idp-server
sourcetype = idp-server-logs
```

#### Splunk検索クエリ

**セキュリティイベント概要**
```spl
index=idp-server 
| eval event_category=case(
    match(event_type, "password_.*"), "認証",
    match(event_type, "fido_uaf_.*|webauthn_.*"), "MFA", 
    match(event_type, "oauth_.*"), "OAuth",
    match(event_type, "federation_.*"), "連携",
    1=1, "その他"
)
| stats count by event_category, event_type
| sort -count
```

**失敗イベントタイムライン**
```spl
index=idp-server "*_failure"
| timechart span=5m count by event_type
```

**ユーザー活動調査**
```spl  
index=idp-server user_id="user123"
| sort _time
| table _time, event_type, client_id, ip_address, event_data
```

## アラート設定

### 1. Grafanaアラート

#### ログイン失敗率高アラート
```yaml
アラート名: ログイン失敗率高
条件: 
  クエリ: sum(rate({job="idp-server"} |= "password_failure" [5m]))
  しきい値: > 10
  評価: 1分間隔で5分間

通知先:
  - Slack: #ops-alerts
  - メール: ops@company.com
  
メッセージ: "ログイン失敗率が高いです: {{ $value }} 失敗/秒"
```

#### MFA登録失敗アラート
```yaml
アラート名: MFA登録問題
条件:
  クエリ: sum(rate({job="idp-server"} |= "registration_failure" [10m]))
  しきい値: > 5
  評価: 2分間隔で10分間

通知先:
  - PagerDuty: P3
  - Slack: #support-team

メッセージ: "MFA登録失敗が多数発生しています"
```

### 2. ELK Stack アラート (Watcher)

#### ブルートフォース検知
```json
{
  "trigger": {
    "schedule": {
      "interval": "1m"
    }
  },
  "input": {
    "search": {
      "request": {
        "body": {
          "query": {
            "bool": {
              "must": [
                {"match": {"event_type": "password_failure"}},
                {"range": {"@timestamp": {"gte": "now-5m"}}}
              ]
            }
          },
          "aggs": {
            "IPアドレス別": {
              "terms": {
                "field": "ip_address.keyword",
                "min_doc_count": 10
              }
            }
          }
        }
      }
    }
  },
  "condition": {
    "compare": {
      "ctx.payload.aggregations.IPアドレス別.buckets.length": {
        "gt": 0
      }
    }
  },
  "actions": {
    "メール送信": {
      "email": {
        "to": ["security@company.com"],
        "subject": "ブルートフォース攻撃の可能性を検出",
        "body": "複数のログイン失敗が発生しているIP: {{ctx.payload.aggregations.IPアドレス別.buckets}}"
      }
    }
  }
}
```

### 3. Splunk アラート

#### 連携失敗アラート
```spl
index=idp-server event_type="federation_failure"
| stats count by provider_name
| where count > 5
```

**アラートアクション**: Slackチャンネルに以下のメッセージを送信:
```
連携プロバイダーで問題が発生しています: {{result.provider_name}} 
失敗回数: {{result.count}}
```

## 監視すべき主要メトリクス

### 1. 認証メトリクス
- **ログイン成功率**: `password_success` / (`password_success` + `password_failure`)
- **MFA成功率**: `*_authentication_success` / (`*_authentication_success` + `*_authentication_failure`)
- **平均ログイン時間**: `password_success`から`oauth_authorize`までの時間

### 2. システム健全性メトリクス
- **エラー率**: `*_failure` イベント数 / 総イベント数
- **応答時間**: APIエンドポイントの応答時間
- **スループット**: タイプ別イベント数/秒

### 3. セキュリティメトリクス
- **IPアドレス別ログイン失敗回数**: IPでグループ化した`password_failure`カウント
- **アカウントロック数**: `user_lock` イベント
- **異常なアクセスパターン**: 通常時間外/場所外のログイン

### 4. ビジネスメトリクス
- **新規ユーザー登録数**: `user_signup` イベント
- **MFA導入率**: デバイス登録ユーザー数 / 総ユーザー数
- **連携利用状況**: プロバイダー別の`federation_success` イベント

## ダッシュボードレイアウト推奨

### 運用ダッシュボード
1. **上段**: 現在のシステム状況（成功率、エラー率）
2. **中段**: リアルタイムイベントストリーム、最近の失敗
3. **下段**: 履歴トレンド、容量メトリクス

### セキュリティダッシュボード  
1. **上段**: ログイン失敗、不審IP、アカウントロック
2. **中段**: MFAイベント、デバイス登録
3. **下段**: 連携イベント、管理者活動

### サポートダッシュボード
1. **上段**: ユーザー検索、最近のユーザー活動
2. **中段**: よくあるエラーパターン、解決ガイド
3. **下段**: チケット相関、エスカレーションメトリクス

## パフォーマンス考慮事項

### ログボリューム管理
- **日次ローテーション**でディスク容量問題を防止
- **7日経過後のアーカイブ圧縮**
- **本番環境での高ボリュームDEBUGイベントのサンプリング**

### クエリ最適化
- **キーフィールドのインデックス化**: user_id, client_id, event_type, timestamp
- **パフォーマンス向上のための時間ベースインデックス**使用
- **応答時間改善のためのクエリ時間範囲制限**

### リソース割り当て
- **Elasticsearch**: 日次1GBログに対して最低4GB RAM
- **Logstash**: ログ解析用に2GB RAM
- **Grafana**: ダッシュボード用に1GB RAM

## 日本特有の考慮事項

### タイムゾーン設定
```yaml
# Grafana設定
timezone: "Asia/Tokyo"

# Elasticsearch設定
PUT _cluster/settings
{
  "persistent": {
    "cluster.time_zone": "Asia/Tokyo"
  }
}
```

### 日本語ログメッセージ対応
```ruby
# Logstash設定での日本語文字処理
filter {
  mutate {
    convert => { 
      "message" => "string" 
    }
  }
}
```

### 営業時間外アラート
```yaml
# 営業時間外（平日19時-9時、土日）のアラート閾値を緩和
アラート条件:
  平日9-19時: エラー率 > 5%
  営業時間外: エラー率 > 10%
```

## 導入ステップ

### フェーズ1: 基本設定（1-2週間）
1. **監視プラットフォーム選択**（既存インフラに基づく）
2. **設定ファイル配置**
3. **基本ダッシュボード作成**

### フェーズ2: アラート設定（1週間）
1. **しきい値の歴史データによるテスト**
2. **アラートルール設定**
3. **通知先設定**

### フェーズ3: 運用開始（1週間）
1. **運用チームトレーニング**
2. **エスカレーション手順策定**
3. **日次/週次チェック手順確立**

## メンテナンス

**月次レビュー項目**:
- ダッシュボード設定の進化する運用ニーズへの適合確認
- アラートしきい値の調整
- 新しいイベントタイプの追加
- パフォーマンス最適化

---

**次のステップ**: 
1. 既存インフラに基づく監視プラットフォーム選択
2. 監視システムへの設定ファイル配置
3. 歴史データでのアラートしきい値テスト
4. 運用チームのダッシュボード使用方法トレーニング