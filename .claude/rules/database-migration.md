---
paths:
  - "libs/idp-server-database/**/*.sql"
---

# データベースマイグレーションのルール

## PostgreSQL + MySQL 両方のマイグレーションが必要

マイグレーションファイルを追加・修正する場合、両DBに対応すること。

```
libs/idp-server-database/
├── postgresql/V0_9_33__new_feature.sql          # PostgreSQL用
└── mysql/V0_9_33__new_feature.mysql.sql         # MySQL用（.mysql.sql 接尾辞が必須）
```

## 命名規則
- PostgreSQL: `V{major}_{minor}_{patch}__description.sql`
- MySQL: `V{major}_{minor}_{patch}__description.mysql.sql`（**`.mysql.sql` 接尾辞が必須**）
- ディレクトリ: `libs/idp-server-database/postgresql/` と `libs/idp-server-database/mysql/`
- 片方だけ追加すると、もう一方のDBで機能が動かない

## PostgreSQL固有機能の注意
- RLS（Row Level Security）はPostgreSQL固有。MySQL版では別の方法でテナント分離する
- パーティショニング構文がDB間で異なる
