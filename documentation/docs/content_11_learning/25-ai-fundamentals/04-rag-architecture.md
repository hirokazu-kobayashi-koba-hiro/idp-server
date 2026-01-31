---
sidebar_position: 4
---

# RAG（Retrieval-Augmented Generation）アーキテクチャ

---

## 概要

RAG（Retrieval-Augmented Generation）は、LLMの知識をリアルタイムで拡張する技術です。外部データソースから関連情報を検索し、それをプロンプトに含めることで、ハルシネーションを減らし、最新情報や専門知識に基づいた回答を生成します。

---

## RAGが解決する問題

### LLMの限界

```
┌─────────────────────────────────────────┐
│      標準的なLLMの制約                   │
├─────────────────────────────────────────┤
│  ❌ 学習データの時間制限                 │
│     → 2024年1月以降の情報は知らない      │
│                                         │
│  ❌ 非公開情報へのアクセスなし           │
│     → 社内文書、契約書、議事録など       │
│                                         │
│  ❌ ハルシネーション（幻覚）             │
│     → 知らないことでも推測で回答         │
│                                         │
│  ❌ ソースの明示不可                     │
│     → 回答根拠が不明確                   │
└─────────────────────────────────────────┘
```

---

### RAGによる解決

```
┌─────────────────────────────────────────┐
│         RAGの仕組み                      │
├─────────────────────────────────────────┤
│  ユーザー質問: "2024年Q4の売上は?"      │
│         ↓                               │
│  ┌───────────────────────────────┐     │
│  │ ベクトル検索                  │     │
│  │ → 関連文書を取得              │     │
│  └───────────────────────────────┘     │
│         ↓                               │
│  取得文書:                              │
│  "2024年Q4売上: 1.2億円（前年比+15%）"  │
│         ↓                               │
│  プロンプト構築:                        │
│  「以下の文書を参照して回答してください  │
│   [取得文書]                            │
│   質問: 2024年Q4の売上は?」             │
│         ↓                               │
│  LLM回答:                               │
│  "2024年Q4の売上は1.2億円です。         │
│   （出典: 財務報告書 2024-Q4）"         │
└─────────────────────────────────────────┘
```

---

## RAGアーキテクチャの全体像

### 基本構成

```
┌─────────────────────────────────────────────────────┐
│                  RAGシステム                         │
├─────────────────────────────────────────────────────┤
│                                                     │
│  1️⃣ インデックス構築フェーズ（オフライン）            │
│  ┌───────────────────────────────────────────────┐ │
│  │ ドキュメント → チャンク分割 → エンベディング  │ │
│  │     ↓              ↓              ↓          │ │
│  │  PDF/MD         テキスト       ベクトル       │ │
│  │                                  ↓            │ │
│  │                          ベクトルDB保存       │ │
│  └───────────────────────────────────────────────┘ │
│                                                     │
│  2️⃣ 検索・生成フェーズ（オンライン）                  │
│  ┌───────────────────────────────────────────────┐ │
│  │ ユーザー質問                                  │ │
│  │     ↓                                        │ │
│  │ エンベディング化                             │ │
│  │     ↓                                        │ │
│  │ ベクトル検索（類似度計算）                   │ │
│  │     ↓                                        │ │
│  │ Top-K文書取得                                │ │
│  │     ↓                                        │ │
│  │ プロンプト構築（質問+文書）                  │ │
│  │     ↓                                        │ │
│  │ LLM生成                                      │ │
│  │     ↓                                        │ │
│  │ 回答（ソース引用付き）                       │ │
│  └───────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────┘
```

---

## インデックス構築

### 1. ドキュメント収集

**対象データソース**:
```
┌─────────────────────────────────────────┐
│      RAGで活用できるデータ               │
├─────────────────────────────────────────┤
│  📄 構造化ドキュメント                   │
│     - PDF, DOCX, Markdown               │
│     - HTML, Notion, Confluence          │
│                                         │
│  💾 データベース                         │
│     - PostgreSQL, MySQL                 │
│     - MongoDB, Elasticsearch            │
│                                         │
│  📊 スプレッドシート                     │
│     - CSV, Excel                        │
│                                         │
│  🌐 Webページ                            │
│     - ドキュメントサイト                 │
│     - ナレッジベース                     │
│                                         │
│  💬 チャット履歴                         │
│     - Slack, Discord                    │
└─────────────────────────────────────────┘
```

---

### 2. チャンキング（分割）

**なぜ分割が必要か**:
```
大きな文書（10,000トークン）
      ↓
検索時に全体がヒット
      ↓
プロンプトに10,000トークン追加
      ↓
❌ コンテキスト圧迫
❌ コスト増大
❌ 無関係情報の混入
```

**チャンキング戦略**:

| 戦略 | サイズ | 用途 |
|------|--------|------|
| **固定長** | 512トークン | 汎用 |
| **段落単位** | 可変 | 文章 |
| **セマンティック** | 意味の切れ目 | 高精度 |
| **コードブロック** | 関数/クラス | ソースコード |

---

**固定長チャンキング例**:
```python
def chunk_text(text, chunk_size=512, overlap=50):
    """
    テキストを重複ありで分割

    Args:
        text: 入力テキスト
        chunk_size: 1チャンクのトークン数
        overlap: 前後チャンクとの重複トークン数
    """
    chunks = []
    start = 0

    while start < len(text):
        end = start + chunk_size
        chunk = text[start:end]
        chunks.append(chunk)
        start += chunk_size - overlap  # 重複を持たせる

    return chunks

# 例
document = "長い文書..." # 2000トークン
chunks = chunk_text(document, chunk_size=512, overlap=50)
# → 4チャンク（重複あり）
```

**重複（Overlap）の重要性**:
```
チャンク1: "...OAuth 2.0は認可プロトコルです。"
チャンク2: "OAuth 2.0は認可プロトコルです。主な役割は..."
           ↑ 重複により文脈が保たれる
```

---

### 3. エンベディング生成

**プロセス**:
```python
from openai import OpenAI

client = OpenAI()

def create_embedding(text):
    """テキストをベクトル化"""
    response = client.embeddings.create(
        model="text-embedding-3-small",  # 1536次元
        input=text
    )
    return response.data[0].embedding

# 例
chunk = "OAuth 2.0は認可フレームワークです"
vector = create_embedding(chunk)
# → [0.023, -0.145, 0.087, ..., 0.234]  (1536次元)
```

---

**エンベディングモデルの選択**:

| モデル | 次元数 | コスト | 用途 |
|--------|--------|--------|------|
| text-embedding-3-small | 1536 | $0.02/1M | 汎用・高速 |
| text-embedding-3-large | 3072 | $0.13/1M | 高精度 |
| voyage-code-2 | 1536 | $0.12/1M | コード検索 |

---

### 4. ベクトルDB保存

**主要ベクトルデータベース**:

```
┌─────────────────────────────────────────┐
│      ベクトルDB比較                      │
├─────────────────────────────────────────┤
│  Pinecone                               │
│  - マネージドサービス                    │
│  - 高速                                 │
│  - スケーラブル                          │
│                                         │
│  Weaviate                               │
│  - オープンソース                        │
│  - ハイブリッド検索                      │
│  - GraphQL API                          │
│                                         │
│  Chroma                                 │
│  - 軽量                                 │
│  - ローカル実行                          │
│  - 開発用途                              │
│                                         │
│  pgvector (PostgreSQL拡張)             │
│  - 既存DBに統合                          │
│  - トランザクション対応                  │
└─────────────────────────────────────────┘
```

---

**pgvectorの例**:
```sql
-- 拡張インストール
CREATE EXTENSION vector;

-- テーブル作成
CREATE TABLE documents (
  id SERIAL PRIMARY KEY,
  content TEXT,
  embedding VECTOR(1536),  -- 1536次元ベクトル
  metadata JSONB
);

-- インデックス作成（高速検索用）
CREATE INDEX ON documents
USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);

-- ドキュメント挿入
INSERT INTO documents (content, embedding, metadata)
VALUES (
  'OAuth 2.0は認可フレームワークです',
  '[0.023, -0.145, ...]',  -- 1536次元ベクトル
  '{"source": "oauth-spec.md", "page": 1}'
);
```

---

## 検索フェーズ

### 1. クエリのエンベディング化

```python
def search(query, top_k=3):
    # 1. クエリをベクトル化
    query_vector = create_embedding(query)

    # 2. ベクトルDB検索
    results = vector_db.search(
        vector=query_vector,
        top_k=top_k,
        metric="cosine"  # コサイン類似度
    )

    return results
```

---

### 2. 類似度計算

**コサイン類似度**:
```
cos_similarity(A, B) = (A · B) / (||A|| × ||B||)

範囲: -1 ～ 1
  1.0  : 完全一致
  0.0  : 無関係
 -1.0  : 正反対
```

**実例**:
```python
query = "OAuth認可フローの説明"
query_vector = [0.1, 0.5, 0.3, ...]

文書1: "OAuth 2.0の認可フロー"
       [0.12, 0.48, 0.31, ...]
       類似度: 0.92  ← Top 1

文書2: "JWTトークンの構造"
       [0.05, 0.15, 0.82, ...]
       類似度: 0.34  ← 除外

文書3: "OAuth 2.0セキュリティ"
       [0.11, 0.47, 0.29, ...]
       類似度: 0.88  ← Top 2
```

---

### 3. ハイブリッド検索

**ベクトル検索の弱点**:
```
クエリ: "RFC 6749"

ベクトル検索結果:
- "OAuth 2.0仕様書" （✅ 正しい）
- "RFC 8252"        （❌ 別のRFC）

→ 固有名詞に弱い
```

**解決策: BM25 + ベクトル検索**:
```
┌─────────────────────────────────────────┐
│      ハイブリッド検索                    │
├─────────────────────────────────────────┤
│  クエリ: "RFC 6749の認可フロー"         │
│                                         │
│  BM25（キーワード検索）                 │
│  ├─ "RFC 6749" 完全一致: スコア 10.0    │
│  └─ "認可フロー" 部分一致: スコア 3.5   │
│                                         │
│  ベクトル検索（意味検索）               │
│  ├─ "OAuth 2.0仕様": スコア 0.92        │
│  └─ "認可コードフロー": スコア 0.85     │
│                                         │
│  スコア統合（重み付け加算）             │
│  最終スコア = 0.7×BM25 + 0.3×Vector     │
└─────────────────────────────────────────┘
```

**実装例**:
```python
def hybrid_search(query, alpha=0.7):
    # BM25検索
    bm25_results = bm25_index.search(query, top_k=10)

    # ベクトル検索
    vector_results = vector_db.search(
        create_embedding(query),
        top_k=10
    )

    # スコア統合
    combined = {}
    for doc in bm25_results:
        combined[doc.id] = alpha * doc.score

    for doc in vector_results:
        combined[doc.id] = combined.get(doc.id, 0) + (1-alpha) * doc.score

    # Top-K取得
    return sorted(combined.items(), key=lambda x: x[1], reverse=True)[:3]
```

---

## 生成フェーズ

### 1. プロンプト構築

**基本テンプレート**:
```python
def build_prompt(query, retrieved_docs):
    context = "\n\n".join([
        f"文書{i+1}: {doc.content}\n出典: {doc.metadata['source']}"
        for i, doc in enumerate(retrieved_docs)
    ])

    prompt = f"""
以下の文書を参照して、質問に回答してください。
文書に記載がない場合は「情報がありません」と答えてください。

【参照文書】
{context}

【質問】
{query}

【回答】
回答:
出典:
"""
    return prompt
```

---

**出力例**:
```
以下の文書を参照して、質問に回答してください。

【参照文書】
文書1: OAuth 2.0は、サードパーティアプリケーションが
ユーザーのリソースへの限定的なアクセスを取得できる
ようにする認可フレームワークです。
出典: oauth-overview.md

文書2: 認可コードフローは、OAuth 2.0で最も安全な
フローであり、クライアントシークレットを保護できる
サーバーサイドアプリケーションで使用されます。
出典: oauth-flows.md

【質問】
OAuth 2.0の最も安全なフローは何ですか?

【回答】
回答: OAuth 2.0の最も安全なフローは認可コードフローです。
これはクライアントシークレットを保護できるサーバーサイド
アプリケーションで使用されます。

出典: oauth-flows.md
```

---

### 2. LLM生成

```python
from openai import OpenAI

def generate_answer(prompt):
    client = OpenAI()

    response = client.chat.completions.create(
        model="gpt-4-turbo",
        messages=[
            {
                "role": "system",
                "content": "あなたは技術文書に基づいて正確に回答するアシスタントです。"
            },
            {
                "role": "user",
                "content": prompt
            }
        ],
        temperature=0.1  # 低温度で事実重視
    )

    return response.choices[0].message.content
```

---

## 高度なRAGテクニック

### 1. 再ランキング（Re-ranking）

**問題**: 初期検索で関連度の低い文書が混入

```
┌─────────────────────────────────────────┐
│      再ランキングの流れ                  │
├─────────────────────────────────────────┤
│  ベクトル検索（高速・低精度）            │
│  → Top 20文書取得                       │
│       ↓                                 │
│  再ランキングモデル（低速・高精度）      │
│  → Top 3文書に絞り込み                  │
│       ↓                                 │
│  LLMに渡す                              │
└─────────────────────────────────────────┘
```

**実装例**:
```python
from sentence_transformers import CrossEncoder

# 再ランキングモデル
reranker = CrossEncoder('cross-encoder/ms-marco-MiniLM-L-6-v2')

def rerank(query, candidates):
    # クエリと各候補のペアでスコア計算
    pairs = [[query, doc.content] for doc in candidates]
    scores = reranker.predict(pairs)

    # スコアでソート
    ranked = sorted(
        zip(candidates, scores),
        key=lambda x: x[1],
        reverse=True
    )

    return [doc for doc, score in ranked[:3]]
```

---

### 2. 自己問い合わせ（Self-Querying）

**概念**: ユーザー質問をメタデータフィルターに変換

```
ユーザー質問: "2024年以降のOAuthセキュリティガイドライン"
      ↓
LLMでパース
      ↓
{
  "semantic_query": "OAuthセキュリティガイドライン",
  "filters": {
    "date": {"gte": "2024-01-01"},
    "category": "security"
  }
}
      ↓
ベクトル検索 + メタデータフィルター
```

---

### 3. 親子チャンキング

**戦略**: 検索は小チャンク、生成には大チャンク

```
┌─────────────────────────────────────────┐
│      親子チャンキング                    │
├─────────────────────────────────────────┤
│  ドキュメント（全体）                    │
│  ├─ セクション1（親）                   │
│  │  ├─ パラグラフ1-1（子）← 検索対象   │
│  │  ├─ パラグラフ1-2（子）             │
│  │  └─ パラグラフ1-3（子）             │
│  ├─ セクション2（親）                   │
│  │  ├─ パラグラフ2-1（子）             │
│  │  └─ パラグラフ2-2（子）             │
└─────────────────────────────────────────┘

検索: パラグラフ1-2 がヒット
      ↓
生成: セクション1（親）全体を使用
      → 文脈が豊富
```

---

### 4. 反復的検索（Iterative Retrieval）

**複雑な質問への対応**:
```
質問: "OAuth 2.0とOpenID Connectの違いは何ですか?"

ステップ1: "OAuth 2.0とは" で検索
  → OAuth 2.0の基本情報を取得

ステップ2: 取得した情報を基に "OpenID Connect" で検索
  → OpenID Connectの情報を取得

ステップ3: 両方の情報を統合して回答生成
```

---

## RAGの評価指標

### 1. 検索品質（Retrieval Quality）

**Precision@K**: 取得した上位K件中の正解率
```
Top 3で取得:
1. ✅ 関連文書
2. ✅ 関連文書
3. ❌ 無関係文書

Precision@3 = 2/3 = 0.67
```

**Recall@K**: 全正解文書のうち取得できた割合
```
正解文書: 5個
Top 3で取得: 2個

Recall@3 = 2/5 = 0.40
```

---

### 2. 生成品質（Generation Quality）

**Answer Relevance**: 回答の関連性
```
質問: "OAuth 2.0のフローは?"
回答: "認可コードフロー、インプリシットフロー..."

→ 高スコア（直接的回答）
```

**Faithfulness**: 文書への忠実性
```
文書: "OAuth 2.0は認可フレームワークです"
回答: "OAuth 2.0は認証フレームワークです"

→ 低スコア（事実改変）
```

---

**評価フレームワーク: RAGAS**
```python
from ragas import evaluate
from ragas.metrics import (
    faithfulness,
    answer_relevancy,
    context_precision,
    context_recall
)

# 評価データ
data = {
    "question": ["OAuth 2.0とは?"],
    "answer": ["OAuth 2.0は認可フレームワークです"],
    "contexts": [["OAuth 2.0は..."]],
    "ground_truth": ["認可フレームワーク"]
}

# 評価実行
result = evaluate(
    data,
    metrics=[faithfulness, answer_relevancy]
)

print(result)
# → faithfulness: 0.95, answer_relevancy: 0.88
```

---

## 実装例: 最小限のRAGシステム

```python
from openai import OpenAI
import numpy as np
from sklearn.metrics.pairwise import cosine_similarity

class SimpleRAG:
    def __init__(self):
        self.client = OpenAI()
        self.documents = []
        self.embeddings = []

    def add_documents(self, docs):
        """ドキュメントをインデックスに追加"""
        for doc in docs:
            # エンベディング生成
            embedding = self.client.embeddings.create(
                model="text-embedding-3-small",
                input=doc
            ).data[0].embedding

            self.documents.append(doc)
            self.embeddings.append(embedding)

    def search(self, query, top_k=3):
        """類似文書を検索"""
        # クエリのエンベディング
        query_embedding = self.client.embeddings.create(
            model="text-embedding-3-small",
            input=query
        ).data[0].embedding

        # コサイン類似度計算
        similarities = cosine_similarity(
            [query_embedding],
            self.embeddings
        )[0]

        # Top-K取得
        top_indices = np.argsort(similarities)[-top_k:][::-1]
        return [self.documents[i] for i in top_indices]

    def answer(self, query):
        """RAGで回答生成"""
        # 関連文書検索
        docs = self.search(query, top_k=3)

        # プロンプト構築
        context = "\n\n".join([f"文書{i+1}: {doc}"
                               for i, doc in enumerate(docs)])

        prompt = f"""
以下の文書を参照して質問に回答してください。

{context}

質問: {query}

回答:
"""

        # LLM生成
        response = self.client.chat.completions.create(
            model="gpt-4-turbo",
            messages=[
                {"role": "system", "content": "正確な回答を心がけてください"},
                {"role": "user", "content": prompt}
            ],
            temperature=0.1
        )

        return response.choices[0].message.content

# 使用例
rag = SimpleRAG()

# ドキュメント追加
rag.add_documents([
    "OAuth 2.0は認可フレームワークです。",
    "OpenID ConnectはOAuth 2.0上に構築された認証レイヤーです。",
    "JWTはJSON Web Tokenの略です。"
])

# 質問
answer = rag.answer("OAuth 2.0とは何ですか?")
print(answer)
# → "OAuth 2.0は認可フレームワークです..."
```

---

## まとめ

### RAGの利点

```
┌─────────────────────────────────────────┐
│         RAGがもたらす価値                │
├─────────────────────────────────────────┤
│  ✅ 最新情報の活用                       │
│     → 学習データの時間制約を超える       │
│                                         │
│  ✅ 専門知識の統合                       │
│     → 社内文書、技術仕様書など           │
│                                         │
│  ✅ ハルシネーション削減                 │
│     → 文書に基づく回答                   │
│                                         │
│  ✅ 透明性                               │
│     → ソース引用で検証可能               │
│                                         │
│  ✅ コスト効率                           │
│     → ファインチューニング不要           │
└─────────────────────────────────────────┘
```

### 課題と対策

| 課題 | 対策 |
|------|------|
| チャンク境界で情報分断 | 重複（Overlap）、親子チャンキング |
| 無関係文書の混入 | 再ランキング、ハイブリッド検索 |
| 複雑な質問への対応 | 反復的検索、自己問い合わせ |
| コンテキスト長制限 | 要約、フィルタリング |

---

## 次のステップ

- [05-ai-agents.md](./05-ai-agents.md) - RAGを活用したAI Agent
- [06-modern-ai-tools.md](./06-modern-ai-tools.md) - RAG実装ツール比較

---

## 参考リンク

- [LangChain RAG Tutorial](https://python.langchain.com/docs/use_cases/question_answering/)
- [LlamaIndex Documentation](https://docs.llamaindex.ai/)
- [pgvector GitHub](https://github.com/pgvector/pgvector)
- [RAGAS Framework](https://github.com/explodinggradients/ragas)
