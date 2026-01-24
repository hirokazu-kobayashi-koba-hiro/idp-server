---
sidebar_position: 0
---

# AI・機械学習基礎

---

## 概要

このセクションでは、現代のAI技術、特に大規模言語モデル（LLM）を中心とした機械学習の基礎を学習します。AI開発の歴史から最新のツール・サービスまで、実践的な観点で解説します。

---

## 学習コンテンツ

### 📚 基礎編

#### [1. AIの歴史と進化](./01-ai-history-evolution.md)
- 1950年代から現在までのAI技術の変遷
- 主要なマイルストーン（Transformer、GPT、Claude等）
- 現在のAI時代の特徴（Foundation Models、マルチモーダル）
- 2025-2026年の最新トレンド

**所要時間**: 30分
**難易度**: ⭐ 入門

---

#### [2. LLM（大規模言語モデル）の基礎](./02-llm-fundamentals.md)
- トークン化の仕組み
- Transformerアーキテクチャ（Self-Attention）
- 学習プロセス（事前学習、ファインチューニング、RLHF）
- パラメータ数とモデルサイズ
- コンテキストウィンドウ
- ハルシネーション対策

**所要時間**: 45分
**難易度**: ⭐⭐ 初級

---

### 🛠️ 実践編

#### [3. プロンプトエンジニアリング入門](./03-prompt-engineering.md)
- 効果的なプロンプトの設計原則
- 高度なプロンプト技法
  - Few-Shot Learning
  - Chain of Thought (CoT)
  - Tree of Thoughts
- タスク別プロンプトパターン
- 実践的な最適化手法

**所要時間**: 60分
**難易度**: ⭐⭐ 初級
**実装**: コード例多数

---

#### [4. RAG（Retrieval-Augmented Generation）](./04-rag-architecture.md)
- RAGアーキテクチャの全体像
- インデックス構築（チャンキング、エンベディング）
- ベクトルデータベース（Pinecone, Weaviate, pgvector）
- 検索と生成フェーズ
- 高度な技法（ハイブリッド検索、再ランキング）
- 評価指標（RAGAS）

**所要時間**: 75分
**難易度**: ⭐⭐⭐ 中級
**実装**: 最小限のRAGシステム実装例

---

### 🤖 応用編

#### [5. AI Agent（自律エージェント）開発](./05-ai-agents.md)
- AI Agentの定義と構成要素
- ReActパターン（Reasoning + Acting）
- ツール使用（OpenAI Function Calling）
- メモリシステム（短期・長期記憶）
- マルチエージェントシステム
- 実用例（コード生成、データ分析、デバッグ）

**所要時間**: 90分
**難易度**: ⭐⭐⭐⭐ 上級
**実装**: ReActエージェント実装例

---

### 🔧 ツール編

#### [6. 最新AIツール・サービス比較](./06-modern-ai-tools.md)
- LLMサービス比較（OpenAI, Anthropic, Google）
- 開発フレームワーク（LangChain, LlamaIndex, AutoGen）
- ベクトルDB比較
- コーディングアシスタント（GitHub Copilot, Cursor）
- 観測性ツール（LangSmith, Helicone）
- 用途別推奨ツールセット

**所要時間**: 45分
**難易度**: ⭐⭐ 初級
**内容**: 2025-2026年の最新情報

---

### 📝 コラム

#### [7. Google NotebookLMを使ってみた](./column-notebooklm-experience.md)
- NotebookLMの概要と特徴
- 実際の使用体験（技術仕様書、会議資料、論文調査等）
- Audio Overview（音声ポッドキャスト）の活用法
- 強み（ソース引用の正確性、複数文書横断、非公開資料対応）
- 限界と注意点（情報範囲、セキュリティ）
- ChatGPT/Claudeとの使い分け
- 実践的な使い方のコツ
- 具体的なユースケース集（職種別）

**所要時間**: 45分
**難易度**: ⭐ 入門
**タイプ**: ツールレビュー・実践

---

#### [8. AIパラドックス - ツールが便利になるほど基礎が重要になる](./column-ai-paradox-fundamentals.md)
- AIパラドックスの本質（なぜ基礎が重要になるのか）
- 5つの理由（質問設計、検証、応用、最適化、統合設計）
- 具体例（プログラミング、ビジネス分析、法務契約）
- 基礎知識の階層（根本原理、設計パターン、技術スタック）
- 学習戦略の転換（積み上げ型→サンドイッチ型）
- 優先して学ぶべき基礎（プログラミング・ビジネス別）
- AI依存症候群の危険性（症状と実例）
- 基礎 × AI = 最強の掛け算の法則

**所要時間**: 75分
**難易度**: ⭐⭐⭐ 中級
**タイプ**: 本質洞察・学習論

---

## 推奨学習パス

```
初学者:
1. AIの歴史と進化
2. LLM基礎
3. プロンプトエンジニアリング
4. 最新AIツール比較
5. コラム（NotebookLM、AIパラドックス）

開発者:
1. LLM基礎
2. プロンプトエンジニアリング
3. RAGアーキテクチャ
4. AI Agent開発
5. 最新AIツール比較
```

---

## 前提知識

このセクションでは以下の知識を前提としています:

- **プログラミング基礎**: Python の基本文法
- **Web技術**: REST API の基本概念
- **データベース**: SQL の基本（RAG編で使用）

---

## 実践環境

実装を試す場合の環境例:

- Python 3.9+
- OpenAI API キー
- Anthropic Claude API キー（オプション）
- ベクトルDB（Chroma等）

---

## 関連リソース

- [OpenAI API Documentation](https://platform.openai.com/docs)
- [Anthropic Claude Documentation](https://docs.anthropic.com/)
- [LangChain Documentation](https://python.langchain.com/)
- [LlamaIndex Documentation](https://docs.llamaindex.ai/)

---

**最終更新**: 2026-01-24
**難易度範囲**: ⭐ 入門 ～ ⭐⭐⭐⭐ 上級
