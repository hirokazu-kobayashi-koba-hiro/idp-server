# PostgreSQL テーブル設計ガイド

このドキュメントでは、PostgreSQLでの効果的なテーブル設計について解説します。
正規化、データ型の選択、制約の活用など、実践的な設計パターンを紹介します。

---

## 目次

1. [テーブル設計の基本原則](#1-テーブル設計の基本原則)
2. [データ型の選択](#2-データ型の選択)
3. [主キーの設計](#3-主キーの設計)
4. [正規化](#4-正規化)
5. [制約（Constraints）](#5-制約constraints)
6. [外部キーとリレーション](#6-外部キーとリレーション)
7. [デフォルト値と自動生成](#7-デフォルト値と自動生成)
8. [実践的な設計パターン](#8-実践的な設計パターン)
9. [アンチパターン](#9-アンチパターン)

---

## 1. テーブル設計の基本原則

### 1.1 良いテーブル設計の特徴

```
┌─────────────────────────────────────────────────────────────────┐
│                  良いテーブル設計の原則                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【単一責任】                                                   │
│  1つのテーブルは1つの概念（エンティティ）を表現                │
│                                                                 │
│  【データの整合性】                                             │
│  制約を使って不正なデータを防止                                 │
│                                                                 │
│  【適切な正規化】                                               │
│  データの重複を排除しつつ、過度な正規化は避ける                │
│                                                                 │
│  【命名規則の統一】                                             │
│  一貫性のある命名でコードの可読性向上                          │
│                                                                 │
│  【将来の拡張性】                                               │
│  スキーマ変更を考慮した設計                                    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 1.2 命名規則

```sql
-- 推奨される命名規則

-- テーブル名: 複数形、スネークケース
CREATE TABLE users (...);
CREATE TABLE order_items (...);
CREATE TABLE user_login_histories (...);

-- カラム名: スネークケース、明確な名前
CREATE TABLE users (
    id BIGINT,
    first_name VARCHAR(50),      -- ○ 明確
    last_name VARCHAR(50),       -- ○ 明確
    email_address VARCHAR(255),  -- ○ 明確
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ
);

-- 外部キー: 参照先テーブル名の単数形 + _id
CREATE TABLE orders (
    id BIGINT,
    user_id BIGINT,              -- usersテーブルを参照
    shipping_address_id BIGINT,  -- addressesテーブルを参照
    ...
);

-- ブール型: is_, has_, can_ などのプレフィックス
CREATE TABLE users (
    is_active BOOLEAN,
    has_verified_email BOOLEAN,
    can_receive_notifications BOOLEAN
);

-- 日時: _at サフィックス
CREATE TABLE orders (
    ordered_at TIMESTAMPTZ,
    shipped_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ
);

-- 日付: _on または _date サフィックス
CREATE TABLE subscriptions (
    start_date DATE,
    end_date DATE
);
```

---

## 2. データ型の選択

### 2.1 数値型

```
┌─────────────────────────────────────────────────────────────────┐
│                        数値型一覧                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【整数型】                                                     │
│  ┌──────────────┬─────────────────┬────────────────────────┐   │
│  │ 型           │ サイズ          │ 範囲                   │   │
│  ├──────────────┼─────────────────┼────────────────────────┤   │
│  │ SMALLINT     │ 2 bytes         │ -32,768 ~ 32,767       │   │
│  │ INTEGER      │ 4 bytes         │ -2.1億 ~ 2.1億         │   │
│  │ BIGINT       │ 8 bytes         │ -922京 ~ 922京         │   │
│  └──────────────┴─────────────────┴────────────────────────┘   │
│                                                                 │
│  【自動採番】                                                   │
│  ┌──────────────┬─────────────────────────────────────────┐   │
│  │ SERIAL       │ INTEGER + シーケンス（レガシー）        │   │
│  │ BIGSERIAL    │ BIGINT + シーケンス（レガシー）         │   │
│  │ IDENTITY     │ 標準SQL準拠（PostgreSQL 10+、推奨）     │   │
│  └──────────────┴─────────────────────────────────────────┘   │
│                                                                 │
│  【小数型】                                                     │
│  ┌──────────────┬─────────────────────────────────────────┐   │
│  │ NUMERIC(p,s) │ 正確な小数（金額計算に使用）            │   │
│  │ REAL         │ 4 bytes 浮動小数点（近似値）            │   │
│  │ DOUBLE       │ 8 bytes 浮動小数点（近似値）            │   │
│  └──────────────┴─────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

```sql
-- 推奨される使い分け

-- ID: BIGINT（将来の拡張性）
CREATE TABLE users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY
);

-- 数量、個数: INTEGER（通常はこれで十分）
CREATE TABLE order_items (
    quantity INTEGER NOT NULL CHECK (quantity > 0)
);

-- 金額: NUMERIC（正確な計算が必要）
CREATE TABLE orders (
    total_amount NUMERIC(12, 2) NOT NULL,  -- 最大10桁.小数2桁
    tax_amount NUMERIC(12, 2) NOT NULL
);

-- 割合、率: NUMERIC
CREATE TABLE tax_rates (
    rate NUMERIC(5, 4) NOT NULL  -- 0.0000 ~ 9.9999 (99.99%)
);

-- 科学計算、座標: DOUBLE PRECISION
CREATE TABLE locations (
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION
);
```

### 2.2 文字列型

```
┌─────────────────────────────────────────────────────────────────┐
│                       文字列型一覧                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┬──────────────────────────────────────────┐   │
│  │ 型           │ 説明                                     │   │
│  ├──────────────┼──────────────────────────────────────────┤   │
│  │ CHAR(n)      │ 固定長、パディングあり（ほぼ使わない）   │   │
│  │ VARCHAR(n)   │ 可変長、最大n文字                        │   │
│  │ TEXT         │ 可変長、制限なし                         │   │
│  └──────────────┴──────────────────────────────────────────┘   │
│                                                                 │
│  【PostgreSQLでの注意点】                                       │
│  ・VARCHAR(n)とTEXTは内部的にほぼ同じ                          │
│  ・長さ制限はCHECK制約でも可能                                 │
│  ・パフォーマンスに差はない                                     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

```sql
-- 推奨される使い分け

-- 短い固定的な値
CREATE TABLE users (
    -- VARCHAR(n)で明示的に制限
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone_number VARCHAR(20),

    -- 固定長コード
    country_code CHAR(2),  -- 'JP', 'US' など
    currency_code CHAR(3)  -- 'JPY', 'USD' など
);

-- 長いテキスト
CREATE TABLE articles (
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,  -- 長さ制限なし
    summary TEXT
);

-- CHECK制約で制限する方法
CREATE TABLE posts (
    content TEXT NOT NULL CHECK (length(content) <= 10000)
);
```

### 2.3 日付・時刻型

```
┌─────────────────────────────────────────────────────────────────┐
│                     日付・時刻型一覧                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌────────────────┬────────────────────────────────────────┐   │
│  │ 型             │ 説明                                   │   │
│  ├────────────────┼────────────────────────────────────────┤   │
│  │ DATE           │ 日付のみ（2024-03-15）                 │   │
│  │ TIME           │ 時刻のみ（14:30:00）                   │   │
│  │ TIMESTAMP      │ 日時（タイムゾーンなし）               │   │
│  │ TIMESTAMPTZ    │ 日時（タイムゾーン付き）★推奨         │   │
│  │ INTERVAL       │ 期間（1 day, 2 hours など）            │   │
│  └────────────────┴────────────────────────────────────────┘   │
│                                                                 │
│  【TIMESTAMP vs TIMESTAMPTZ】                                   │
│  ・TIMESTAMP: タイムゾーン情報なし、そのまま保存               │
│  ・TIMESTAMPTZ: UTC変換して保存、取得時にクライアントTZに変換  │
│  ・Webアプリケーションでは TIMESTAMPTZ を推奨                  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

```sql
-- 推奨される使い分け

CREATE TABLE events (
    -- イベント発生日時: TIMESTAMPTZ（タイムゾーン対応）
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    scheduled_at TIMESTAMPTZ,

    -- 誕生日など日付のみ: DATE
    birth_date DATE,

    -- 営業時間など時刻のみ: TIME
    opening_time TIME,
    closing_time TIME,

    -- 期間: INTERVAL
    duration INTERVAL
);

-- タイムゾーンの設定
SET timezone = 'Asia/Tokyo';

-- INTERVALの使用例
SELECT
    now() AS current_time,
    now() + INTERVAL '1 day' AS tomorrow,
    now() - INTERVAL '1 week' AS last_week,
    now() + INTERVAL '2 hours 30 minutes' AS later;
```

### 2.4 その他の型

```sql
-- ブール型
CREATE TABLE users (
    is_active BOOLEAN NOT NULL DEFAULT true,
    has_verified_email BOOLEAN NOT NULL DEFAULT false
);

-- UUID型
CREATE TABLE api_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key_value UUID NOT NULL UNIQUE DEFAULT gen_random_uuid()
);

-- JSON型
CREATE TABLE user_preferences (
    user_id BIGINT PRIMARY KEY,
    -- JSONB: バイナリ形式、インデックス可能、推奨
    settings JSONB NOT NULL DEFAULT '{}',
    -- JSON: テキスト形式、入力順序を保持
    raw_data JSON
);

-- 配列型
CREATE TABLE articles (
    id BIGINT PRIMARY KEY,
    tags TEXT[] NOT NULL DEFAULT '{}',
    scores INTEGER[]
);

-- ENUM型（限定値）
CREATE TYPE order_status AS ENUM ('pending', 'processing', 'completed', 'cancelled');
CREATE TABLE orders (
    id BIGINT PRIMARY KEY,
    status order_status NOT NULL DEFAULT 'pending'
);
```

---

## 3. 主キーの設計

### 3.1 主キーの種類

```
┌─────────────────────────────────────────────────────────────────┐
│                     主キーの種類                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【サロゲートキー（代理キー）】                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ ・システムが自動生成するID                               │   │
│  │ ・ビジネス的な意味を持たない                             │   │
│  │ ・変更されることがない                                   │   │
│  │ ・例: SERIAL, UUID, IDENTITY                            │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  【ナチュラルキー（自然キー）】                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ ・ビジネス上の識別子                                     │   │
│  │ ・変更される可能性がある                                 │   │
│  │ ・例: メールアドレス、社員番号、ISBN                     │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  【推奨】サロゲートキーを主キーに、                             │
│         ナチュラルキーはユニーク制約で                          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 IDENTITY（推奨）

```sql
-- GENERATED ALWAYS AS IDENTITY（PostgreSQL 10+）
CREATE TABLE users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE  -- ナチュラルキーはUNIQUE
);

-- GENERATED BY DEFAULT（値を指定可能）
CREATE TABLE imported_users (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    external_id BIGINT
);

-- シーケンスのカスタマイズ
CREATE TABLE orders (
    id BIGINT GENERATED ALWAYS AS IDENTITY (
        START WITH 1000
        INCREMENT BY 1
        MINVALUE 1000
        MAXVALUE 9999999999
        CACHE 20
    ) PRIMARY KEY
);
```

### 3.3 UUID

```sql
-- UUID主キー
CREATE TABLE sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- UUIDの利点と欠点
-- ○ 分散システムでID衝突なし
-- ○ IDから情報が推測されにくい（セキュリティ）
-- ○ マイグレーション時に便利
-- × サイズが大きい（16 bytes vs 8 bytes）
-- × インデックス効率がやや低下
-- × ソート順がランダム
```

### 3.4 複合主キー

```sql
-- 中間テーブル（多対多）
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id),
    role_id BIGINT NOT NULL REFERENCES roles(id),
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, role_id)
);

-- タイムシリーズデータ
CREATE TABLE sensor_readings (
    sensor_id BIGINT NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    value NUMERIC(10, 2) NOT NULL,
    PRIMARY KEY (sensor_id, recorded_at)
);
```

---

## 4. 正規化

### 4.1 正規化の目的

```
┌─────────────────────────────────────────────────────────────────┐
│                     正規化の目的                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【データの重複を排除】                                         │
│  → ストレージの節約                                            │
│  → 更新異常の防止                                              │
│                                                                 │
│  【更新異常の種類】                                             │
│  ・挿入異常: 関連データがないと挿入できない                    │
│  ・更新異常: 同じデータを複数箇所で更新が必要                  │
│  ・削除異常: 他のデータも一緒に削除される                      │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 4.2 正規化の段階

```
┌─────────────────────────────────────────────────────────────────┐
│                    正規化の段階                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【第1正規形 (1NF)】                                            │
│  ・各カラムが原子値（分割不可能な値）                          │
│  ・繰り返しグループがない                                      │
│                                                                 │
│  ❌ 非正規形                                                   │
│  orders: id, items (カンマ区切り: "item1, item2, item3")       │
│                                                                 │
│  ✅ 第1正規形                                                  │
│  orders: id                                                     │
│  order_items: id, order_id, item_name                          │
│                                                                 │
│  ──────────────────────────────────────────────                │
│                                                                 │
│  【第2正規形 (2NF)】                                            │
│  ・第1正規形 +                                                  │
│  ・部分関数従属の排除（複合キーの一部にのみ依存する属性を分離）│
│                                                                 │
│  ❌ 第1正規形だが第2正規形でない                               │
│  order_items: (order_id, product_id), product_name, quantity   │
│  → product_name は product_id のみに依存                       │
│                                                                 │
│  ✅ 第2正規形                                                  │
│  order_items: (order_id, product_id), quantity                 │
│  products: product_id, product_name                            │
│                                                                 │
│  ──────────────────────────────────────────────                │
│                                                                 │
│  【第3正規形 (3NF)】                                            │
│  ・第2正規形 +                                                  │
│  ・推移的関数従属の排除（非キー属性が他の非キー属性に依存しない）│
│                                                                 │
│  ❌ 第2正規形だが第3正規形でない                               │
│  users: id, department_id, department_name                     │
│  → department_name は department_id に依存（推移的依存）       │
│                                                                 │
│  ✅ 第3正規形                                                  │
│  users: id, department_id                                      │
│  departments: id, name                                         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 4.3 正規化の例

```sql
-- ❌ 非正規化テーブル
CREATE TABLE orders_denormalized (
    order_id BIGINT,
    customer_name VARCHAR(100),
    customer_email VARCHAR(255),
    customer_address TEXT,
    product_names TEXT,  -- カンマ区切り
    product_prices TEXT, -- カンマ区切り
    total_amount NUMERIC
);

-- ✅ 正規化されたテーブル
CREATE TABLE customers (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE addresses (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(id),
    address_line TEXT NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE products (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    price NUMERIC(10, 2) NOT NULL
);

CREATE TABLE orders (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(id),
    shipping_address_id BIGINT REFERENCES addresses(id),
    total_amount NUMERIC(12, 2) NOT NULL,
    ordered_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE order_items (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    product_id BIGINT NOT NULL REFERENCES products(id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(10, 2) NOT NULL  -- 注文時点の価格を保存
);
```

### 4.4 非正規化の判断

```
┌─────────────────────────────────────────────────────────────────┐
│                  非正規化が許容されるケース                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【パフォーマンスが重要な場合】                                 │
│  ・頻繁に結合されるデータ                                      │
│  ・読み取りが圧倒的に多い場合                                  │
│  → 計算済みの値やコピーを保持                                  │
│                                                                 │
│  【履歴データの保存】                                           │
│  ・注文時点の商品名・価格                                      │
│  ・スナップショットが必要なデータ                              │
│                                                                 │
│  【キャッシュとしての重複】                                     │
│  ・マテリアライズドビュー                                      │
│  ・集計テーブル                                                │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

```sql
-- 非正規化の例: 注文時点のデータを保存
CREATE TABLE order_items (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    -- 正規化なら products テーブルを参照するが、
    -- 注文時点の値を保存するため非正規化
    product_name VARCHAR(200) NOT NULL,
    unit_price NUMERIC(10, 2) NOT NULL
);

-- 非正規化の例: 集計値のキャッシュ
CREATE TABLE user_stats (
    user_id BIGINT PRIMARY KEY REFERENCES users(id),
    order_count INTEGER NOT NULL DEFAULT 0,
    total_spent NUMERIC(12, 2) NOT NULL DEFAULT 0,
    last_order_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

---

## 5. 制約（Constraints）

### 5.1 制約の種類

```sql
-- NOT NULL: NULL値を禁止
CREATE TABLE users (
    id BIGINT PRIMARY KEY,
    email VARCHAR(255) NOT NULL  -- 必須フィールド
);

-- UNIQUE: 一意性制約
CREATE TABLE users (
    id BIGINT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,  -- 単一カラム
    UNIQUE (first_name, last_name)       -- 複合ユニーク
);

-- PRIMARY KEY: 主キー（NOT NULL + UNIQUE）
CREATE TABLE users (
    id BIGINT PRIMARY KEY
);

-- FOREIGN KEY: 外部キー
CREATE TABLE orders (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id)
);

-- CHECK: 値の検証
CREATE TABLE products (
    id BIGINT PRIMARY KEY,
    price NUMERIC(10, 2) NOT NULL CHECK (price >= 0),
    discount_rate NUMERIC(3, 2) CHECK (discount_rate BETWEEN 0 AND 1),
    stock INTEGER NOT NULL CHECK (stock >= 0)
);

-- EXCLUDE: 排他制約（範囲の重複禁止など）
CREATE TABLE reservations (
    id BIGINT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    reserved_period TSTZRANGE NOT NULL,
    EXCLUDE USING GIST (room_id WITH =, reserved_period WITH &&)
);
```

### 5.2 CHECK制約の活用

```sql
-- 値の範囲制限
CREATE TABLE orders (
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('pending', 'processing', 'completed', 'cancelled')),
    quantity INTEGER NOT NULL CHECK (quantity > 0 AND quantity <= 1000),
    discount_percent NUMERIC(5, 2) CHECK (discount_percent BETWEEN 0 AND 100)
);

-- 条件付き必須
CREATE TABLE deliveries (
    id BIGINT PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    delivered_at TIMESTAMPTZ,
    -- completed の場合は delivered_at が必須
    CHECK (status != 'completed' OR delivered_at IS NOT NULL)
);

-- カラム間の整合性
CREATE TABLE date_ranges (
    id BIGINT PRIMARY KEY,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    CHECK (end_date >= start_date)
);

-- メールアドレスの簡易検証
CREATE TABLE users (
    email VARCHAR(255) NOT NULL
        CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$')
);
```

### 5.3 制約の命名

```sql
-- 制約に名前を付ける（エラーメッセージが分かりやすくなる）
CREATE TABLE products (
    id BIGINT PRIMARY KEY,
    code VARCHAR(20) NOT NULL,
    price NUMERIC(10, 2) NOT NULL,
    stock INTEGER NOT NULL,

    CONSTRAINT products_code_unique UNIQUE (code),
    CONSTRAINT products_price_positive CHECK (price >= 0),
    CONSTRAINT products_stock_non_negative CHECK (stock >= 0)
);

-- 外部キーにも名前を付ける
CREATE TABLE orders (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,

    CONSTRAINT orders_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);
```

---

## 6. 外部キーとリレーション

### 6.1 リレーションの種類

```
┌─────────────────────────────────────────────────────────────────┐
│                   リレーションの種類                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【1対1 (One-to-One)】                                          │
│  users ──────────── user_profiles                              │
│  ・ユーザーごとに1つのプロフィール                             │
│  ・外部キー + UNIQUE制約                                        │
│                                                                 │
│  【1対多 (One-to-Many)】                                        │
│  users ──────────┬─ orders                                     │
│                  ├─ orders                                     │
│                  └─ orders                                     │
│  ・1人のユーザーが複数の注文                                   │
│  ・最も一般的なパターン                                        │
│                                                                 │
│  【多対多 (Many-to-Many)】                                      │
│  users ──┬── user_roles ──┬── roles                            │
│          └───────────────┘                                     │
│  ・中間テーブルで表現                                          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 6.2 外部キーの定義

```sql
-- 1対1
CREATE TABLE user_profiles (
    user_id BIGINT PRIMARY KEY REFERENCES users(id),
    bio TEXT,
    avatar_url VARCHAR(500)
);

-- 1対多
CREATE TABLE orders (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    total_amount NUMERIC(12, 2) NOT NULL
);

-- 多対多
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id),
    role_id BIGINT NOT NULL REFERENCES roles(id),
    PRIMARY KEY (user_id, role_id)
);
```

### 6.3 参照アクション

```sql
-- ON DELETE / ON UPDATE のオプション

CREATE TABLE orders (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,

    CONSTRAINT orders_user_fkey
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE      -- ユーザー削除時に注文も削除
        ON UPDATE CASCADE      -- ユーザーID変更時に追従
);

-- オプション一覧:
-- RESTRICT: 参照されている場合は削除/更新を禁止（デフォルト）
-- CASCADE: 参照先も一緒に削除/更新
-- SET NULL: NULLに設定
-- SET DEFAULT: デフォルト値に設定
-- NO ACTION: RESTRICTと同様（トランザクション終了時にチェック）
```

```sql
-- 使い分けの例

-- ユーザー削除時に関連データも削除（CASCADE）
CREATE TABLE sessions (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE
);

-- 親カテゴリ削除時は禁止（RESTRICT）
CREATE TABLE categories (
    id BIGINT PRIMARY KEY,
    parent_id BIGINT REFERENCES categories(id) ON DELETE RESTRICT
);

-- 担当者退職時はNULLに（SET NULL）
CREATE TABLE projects (
    id BIGINT PRIMARY KEY,
    owner_id BIGINT REFERENCES users(id) ON DELETE SET NULL
);
```

---

## 7. デフォルト値と自動生成

### 7.1 デフォルト値

```sql
CREATE TABLE articles (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- 固定値
    status VARCHAR(20) NOT NULL DEFAULT 'draft',
    is_published BOOLEAN NOT NULL DEFAULT false,
    view_count INTEGER NOT NULL DEFAULT 0,

    -- 関数呼び出し
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- UUID
    public_id UUID NOT NULL DEFAULT gen_random_uuid(),

    -- 式
    slug VARCHAR(200) UNIQUE  -- 通常はアプリケーション側で設定
);
```

### 7.2 生成列（Generated Columns）

```sql
-- PostgreSQL 12+: STORED（保存される計算列）
CREATE TABLE products (
    id BIGINT PRIMARY KEY,
    price NUMERIC(10, 2) NOT NULL,
    tax_rate NUMERIC(3, 2) NOT NULL DEFAULT 0.10,

    -- 自動計算される列
    tax_amount NUMERIC(10, 2)
        GENERATED ALWAYS AS (price * tax_rate) STORED,
    total_price NUMERIC(10, 2)
        GENERATED ALWAYS AS (price * (1 + tax_rate)) STORED
);

-- フルネームの自動生成
CREATE TABLE users (
    id BIGINT PRIMARY KEY,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    full_name VARCHAR(101)
        GENERATED ALWAYS AS (first_name || ' ' || last_name) STORED
);
```

### 7.3 トリガーによる自動更新

```sql
-- updated_at を自動更新するトリガー
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_updated_at
    BEFORE UPDATE ON articles
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at();

-- 複数テーブルに適用
CREATE TRIGGER trigger_update_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at();
```

---

## 8. 実践的な設計パターン

### 8.1 監査カラム

```sql
-- 全テーブルに共通の監査カラム
CREATE TABLE orders (
    id BIGINT PRIMARY KEY,
    -- ビジネスカラム
    user_id BIGINT NOT NULL,
    total_amount NUMERIC(12, 2) NOT NULL,

    -- 監査カラム
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by BIGINT REFERENCES users(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by BIGINT REFERENCES users(id)
);
```

### 8.2 ソフトデリート

```sql
-- 物理削除せずに論理削除
CREATE TABLE users (
    id BIGINT PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT
);

-- ユニーク制約は削除されていないレコードのみに適用
CREATE UNIQUE INDEX users_email_unique
    ON users (email)
    WHERE is_deleted = false;

-- アクティブなユーザーのみ取得するビュー
CREATE VIEW active_users AS
    SELECT * FROM users WHERE is_deleted = false;
```

### 8.3 履歴テーブル

```sql
-- 変更履歴を保存
CREATE TABLE user_histories (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    -- 変更前の値をJSONBで保存
    old_values JSONB,
    -- 変更後の値
    new_values JSONB,
    -- 変更種別
    operation VARCHAR(10) NOT NULL CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE')),
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    changed_by BIGINT
);

-- トリガーで自動記録
CREATE OR REPLACE FUNCTION record_user_history()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO user_histories (user_id, old_values, new_values, operation)
    VALUES (
        COALESCE(NEW.id, OLD.id),
        CASE WHEN TG_OP = 'INSERT' THEN NULL ELSE to_jsonb(OLD) END,
        CASE WHEN TG_OP = 'DELETE' THEN NULL ELSE to_jsonb(NEW) END,
        TG_OP
    );
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;
```

### 8.4 多態性（ポリモーフィズム）

```sql
-- STI (Single Table Inheritance)
CREATE TABLE notifications (
    id BIGINT PRIMARY KEY,
    type VARCHAR(50) NOT NULL,  -- 'email', 'sms', 'push'
    user_id BIGINT NOT NULL,
    -- 共通カラム
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    sent_at TIMESTAMPTZ,
    -- タイプ固有のデータ
    metadata JSONB NOT NULL DEFAULT '{}'
);

-- または別テーブルに分離
CREATE TABLE notifications (
    id BIGINT PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL
);

CREATE TABLE email_notification_details (
    notification_id BIGINT PRIMARY KEY REFERENCES notifications(id),
    email_address VARCHAR(255) NOT NULL,
    subject VARCHAR(200) NOT NULL
);

CREATE TABLE sms_notification_details (
    notification_id BIGINT PRIMARY KEY REFERENCES notifications(id),
    phone_number VARCHAR(20) NOT NULL
);
```

---

## 9. アンチパターン

### 9.1 避けるべきパターン

```
┌─────────────────────────────────────────────────────────────────┐
│                    アンチパターン                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ❌ EAV (Entity-Attribute-Value)                               │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ entity_id | attribute   | value                         │   │
│  │ 1         | 'name'      | 'Alice'                       │   │
│  │ 1         | 'email'     | 'alice@example.com'           │   │
│  │ 1         | 'age'       | '30'                          │   │
│  └─────────────────────────────────────────────────────────┘   │
│  → クエリが複雑、型安全性なし、インデックス効率悪い            │
│  → JSONBカラムまたは適切なテーブル設計を検討                   │
│                                                                 │
│  ❌ 神テーブル（1テーブルに全てを詰め込む）                    │
│  → 適切に正規化する                                            │
│                                                                 │
│  ❌ カンマ区切りの値                                           │
│  tags = 'java,python,go'                                       │
│  → 配列型または中間テーブルを使用                              │
│                                                                 │
│  ❌ 曖昧なカラム名                                             │
│  data, value, info, flag, type                                 │
│  → 具体的で説明的な名前を使用                                  │
│                                                                 │
│  ❌ 予約語をカラム名に使用                                     │
│  user, order, table, index                                     │
│  → users, orders など複数形や別名を使用                        │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 9.2 修正例

```sql
-- ❌ EAV（避けるべき）
CREATE TABLE user_attributes (
    user_id BIGINT,
    attribute_name VARCHAR(50),
    attribute_value TEXT
);

-- ✅ 適切なテーブル設計
CREATE TABLE users (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    age INTEGER
);

-- または動的な属性が必要な場合はJSONB
CREATE TABLE users (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    attributes JSONB NOT NULL DEFAULT '{}'
);

-- ❌ カンマ区切り（避けるべき）
CREATE TABLE articles (
    id BIGINT PRIMARY KEY,
    tags VARCHAR(500)  -- 'java,python,go'
);

-- ✅ 配列型
CREATE TABLE articles (
    id BIGINT PRIMARY KEY,
    tags TEXT[] NOT NULL DEFAULT '{}'
);

-- ✅ または中間テーブル
CREATE TABLE article_tags (
    article_id BIGINT REFERENCES articles(id),
    tag_id BIGINT REFERENCES tags(id),
    PRIMARY KEY (article_id, tag_id)
);
```

---

## 参考リンク

- [PostgreSQL公式ドキュメント - データ型](https://www.postgresql.org/docs/current/datatype.html)
- [PostgreSQL公式ドキュメント - 制約](https://www.postgresql.org/docs/current/ddl-constraints.html)
- [PostgreSQL公式ドキュメント - 外部キー](https://www.postgresql.org/docs/current/ddl-constraints.html#DDL-CONSTRAINTS-FK)
