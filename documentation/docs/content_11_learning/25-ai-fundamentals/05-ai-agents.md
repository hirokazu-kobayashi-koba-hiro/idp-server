---
sidebar_position: 5
---

# AI Agent（自律エージェント）開発入門

---

## 概要

AI Agent（エージェント）は、目標を与えられると自律的にタスクを計画・実行し、ツールを使いながら複雑な問題を解決するシステムです。従来のLLM対話を超えて、「考え、行動し、学習する」能力を持ちます。

---

## AI Agentとは

### 従来のLLM vs AI Agent

```
┌─────────────────────────────────────────────────────┐
│          従来のLLM（単発対話）                       │
├─────────────────────────────────────────────────────┤
│  ユーザー: "Pythonでファイルを読む方法は?"          │
│      ↓                                              │
│  LLM: "open()関数を使います..."                     │
│      ↓                                              │
│  完了（1回のやりとり）                              │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│            AI Agent（自律的実行）                    │
├─────────────────────────────────────────────────────┤
│  ユーザー: "リポジトリのREADMEを分析して、           │
│            改善提案をIssueとして登録して"            │
│      ↓                                              │
│  Agent思考: "READMEを読む必要がある"                │
│      ↓                                              │
│  ツール実行: read_file("README.md")                 │
│      ↓                                              │
│  Agent思考: "内容を分析..."                         │
│      ↓                                              │
│  Agent思考: "GitHub APIでIssue作成が必要"           │
│      ↓                                              │
│  ツール実行: create_issue(title=..., body=...)      │
│      ↓                                              │
│  完了報告: "Issue #123を作成しました"               │
└─────────────────────────────────────────────────────┘
```

---

### AI Agentの定義

**5つの主要要素**:

```
┌─────────────────────────────────────────┐
│       AI Agentの構成要素                 │
├─────────────────────────────────────────┤
│  1️⃣ 知覚（Perception）                   │
│     環境やタスクの状態を理解             │
│                                         │
│  2️⃣ 思考（Reasoning）                    │
│     次のアクションを決定                 │
│                                         │
│  3️⃣ 行動（Action）                       │
│     ツールを使って実行                   │
│                                         │
│  4️⃣ 記憶（Memory）                       │
│     過去の行動を記録・参照               │
│                                         │
│  5️⃣ 学習（Learning）                     │
│     失敗から改善（オプション）           │
└─────────────────────────────────────────┘
```

---

## ReAct パターン（Reasoning + Acting）

### 概念

**ReAct**: Reasoning（推論）とActing（行動）を交互に繰り返す

```
┌─────────────────────────────────────────┐
│         ReActループ                      │
├─────────────────────────────────────────┤
│  タスク: "2024年のノーベル物理学賞受賞者は?" │
│                                         │
│  1️⃣ Thought（思考）:                     │
│     "最新情報が必要。Webで検索しよう"   │
│       ↓                                 │
│  2️⃣ Action（行動）:                      │
│     web_search("2024 ノーベル物理学賞")  │
│       ↓                                 │
│  3️⃣ Observation（観察）:                 │
│     "検索結果: ジョン・ホプフィールドと..."│
│       ↓                                 │
│  4️⃣ Thought（思考）:                     │
│     "情報を得た。回答できる"             │
│       ↓                                 │
│  5️⃣ Answer（回答）:                      │
│     "2024年のノーベル物理学賞は..."      │
└─────────────────────────────────────────┘
```

---

### 実装例

```python
from openai import OpenAI
import json

class ReActAgent:
    def __init__(self):
        self.client = OpenAI()
        self.tools = {
            "web_search": self.web_search,
            "calculator": self.calculator,
            "read_file": self.read_file
        }

    def run(self, task, max_iterations=5):
        """ReActループの実行"""
        messages = [
            {"role": "system", "content": REACT_SYSTEM_PROMPT},
            {"role": "user", "content": task}
        ]

        for i in range(max_iterations):
            # LLMに思考させる
            response = self.client.chat.completions.create(
                model="gpt-4-turbo",
                messages=messages,
                temperature=0.1
            )

            content = response.choices[0].message.content
            print(f"\n--- Iteration {i+1} ---")
            print(content)

            # 思考の解析
            if "Action:" in content:
                # アクション抽出
                action_line = [l for l in content.split("\n")
                               if l.startswith("Action:")][0]
                tool_call = action_line.replace("Action:", "").strip()

                # ツール実行
                result = self.execute_tool(tool_call)

                # 観察結果を追加
                messages.append({"role": "assistant", "content": content})
                messages.append({
                    "role": "user",
                    "content": f"Observation: {result}"
                })

            elif "Answer:" in content:
                # 最終回答
                return content.split("Answer:")[1].strip()

        return "タスク未完了（最大反復回数到達）"

    def execute_tool(self, tool_call):
        """ツール実行"""
        # 例: "web_search('2024 ノーベル賞')"
        tool_name = tool_call.split("(")[0]
        args_str = tool_call.split("(")[1].rstrip(")")

        if tool_name in self.tools:
            return self.tools[tool_name](args_str)
        else:
            return f"Error: Unknown tool {tool_name}"

    # ツール実装
    def web_search(self, query):
        # 実際はAPIを呼ぶ（簡略版）
        return f"検索結果: {query} に関する情報..."

    def calculator(self, expression):
        try:
            return str(eval(expression))
        except Exception as e:
            return f"計算エラー: {e}"

    def read_file(self, filepath):
        try:
            with open(filepath, 'r') as f:
                return f.read()
        except Exception as e:
            return f"ファイル読み込みエラー: {e}"

# システムプロンプト
REACT_SYSTEM_PROMPT = """
あなたはReActフレームワークに従って動作するAgentです。

各反復で以下のフォーマットで出力してください:

Thought: [現状分析と次の行動計画]
Action: [tool_name('argument')]
（観察結果を受け取る）

または

Thought: [最終的な結論]
Answer: [最終回答]

利用可能なツール:
- web_search(query): Web検索
- calculator(expression): 数式計算
- read_file(path): ファイル読み込み
"""

# 使用例
agent = ReActAgent()
result = agent.run("1234 × 5678 を計算して、結果をresult.txtに保存して")
print(f"\n最終結果: {result}")
```

---

**実行例**:
```
--- Iteration 1 ---
Thought: まず1234 × 5678を計算する必要がある
Action: calculator('1234 * 5678')

--- Iteration 2 ---
Observation: 7006652

Thought: 計算結果を得た。次にファイルに保存する必要がある
Action: write_file('result.txt', '7006652')

--- Iteration 3 ---
Observation: ファイル書き込み成功

Thought: タスク完了
Answer: 1234 × 5678 = 7006652 を result.txt に保存しました。
```

---

## ツール使用（Tool Use / Function Calling）

### OpenAI Function Calling

**仕組み**: LLMに利用可能なツールを教え、適切に呼び出させる

```python
from openai import OpenAI

client = OpenAI()

# ツール定義
tools = [
    {
        "type": "function",
        "function": {
            "name": "get_weather",
            "description": "指定都市の天気を取得",
            "parameters": {
                "type": "object",
                "properties": {
                    "location": {
                        "type": "string",
                        "description": "都市名（例: 東京）"
                    },
                    "unit": {
                        "type": "string",
                        "enum": ["celsius", "fahrenheit"]
                    }
                },
                "required": ["location"]
            }
        }
    }
]

# LLM呼び出し
response = client.chat.completions.create(
    model="gpt-4-turbo",
    messages=[{"role": "user", "content": "東京の天気は?"}],
    tools=tools,
    tool_choice="auto"  # 自動でツール選択
)

# ツール呼び出しの確認
if response.choices[0].message.tool_calls:
    tool_call = response.choices[0].message.tool_calls[0]
    function_name = tool_call.function.name
    arguments = json.loads(tool_call.function.arguments)

    print(f"Tool: {function_name}")
    print(f"Args: {arguments}")
    # → Tool: get_weather
    # → Args: {'location': '東京', 'unit': 'celsius'}

    # 実際にツールを実行
    result = get_weather(**arguments)

    # 結果をLLMに返す
    messages.append(response.choices[0].message)
    messages.append({
        "role": "tool",
        "tool_call_id": tool_call.id,
        "content": json.dumps(result)
    })

    final_response = client.chat.completions.create(
        model="gpt-4-turbo",
        messages=messages
    )

    print(final_response.choices[0].message.content)
    # → "東京の天気は晴れで、気温は25度です。"
```

---

### ツール設計のベストプラクティス

**1. 明確な説明**
```python
{
    "name": "search_codebase",
    "description": """
コードベース内を検索します。
ファイル名、関数名、クラス名、コメントを対象に
正規表現パターンマッチングを行います。

使用例:
- "UserService クラスを探す" → search_codebase('class UserService')
- "認証関連のファイル" → search_codebase('auth.*\\.py')
    """,
    "parameters": { ... }
}
```

**2. 型とバリデーション**
```python
{
    "parameters": {
        "type": "object",
        "properties": {
            "file_path": {
                "type": "string",
                "description": "ファイルパス（絶対パスまたは相対パス）",
                "pattern": "^[a-zA-Z0-9/_.-]+$"  # パスインジェクション対策
            },
            "line_number": {
                "type": "integer",
                "minimum": 1,
                "maximum": 100000
            }
        },
        "required": ["file_path"]
    }
}
```

---

## メモリシステム

### 短期記憶（Working Memory）

**会話履歴の保持**:
```python
class Agent:
    def __init__(self):
        self.conversation_history = []

    def chat(self, user_message):
        # ユーザーメッセージを追加
        self.conversation_history.append({
            "role": "user",
            "content": user_message
        })

        # LLM呼び出し（全履歴を渡す）
        response = client.chat.completions.create(
            model="gpt-4-turbo",
            messages=self.conversation_history
        )

        # アシスタント応答を記録
        assistant_message = response.choices[0].message.content
        self.conversation_history.append({
            "role": "assistant",
            "content": assistant_message
        })

        return assistant_message

# 使用例
agent = Agent()
agent.chat("Pythonでファイルを読む方法は?")
agent.chat("それをJSON形式で保存するには?")  # 前の文脈を覚えている
```

---

### 長期記憶（Long-term Memory）

**ベクトルDBによる記憶**:
```python
class AgentWithMemory:
    def __init__(self):
        self.vector_db = VectorDB()
        self.session_history = []

    def remember(self, key, value):
        """重要な情報を長期記憶に保存"""
        self.vector_db.add({
            "key": key,
            "value": value,
            "timestamp": datetime.now(),
            "embedding": create_embedding(f"{key}: {value}")
        })

    def recall(self, query, top_k=3):
        """類似の記憶を検索"""
        query_embedding = create_embedding(query)
        results = self.vector_db.search(query_embedding, top_k=top_k)
        return results

    def chat(self, user_message):
        # 関連する過去の記憶を検索
        memories = self.recall(user_message)

        # メモリをコンテキストに追加
        context = "過去の関連情報:\n" + "\n".join([
            f"- {m['key']}: {m['value']}" for m in memories
        ])

        # プロンプト構築
        messages = [
            {"role": "system", "content": context},
            *self.session_history,
            {"role": "user", "content": user_message}
        ]

        # LLM呼び出し
        response = client.chat.completions.create(
            model="gpt-4-turbo",
            messages=messages
        )

        return response.choices[0].message.content

# 使用例
agent = AgentWithMemory()
agent.remember("preferred_language", "Python")
agent.remember("project_name", "idp-server")

response = agent.chat("コード例を示して")
# → Pythonで、idp-serverプロジェクトの文脈で回答
```

---

## 計画（Planning）

### タスク分解

**Chain of Thought Planning**:
```python
def plan_task(task):
    """タスクを段階的に分解"""
    prompt = f"""
以下のタスクを実行可能なステップに分解してください。

タスク: {task}

出力形式（JSON）:
{{
  "steps": [
    {{"id": 1, "action": "...", "tool": "..."}},
    {{"id": 2, "action": "...", "tool": "...", "depends_on": [1]}}
  ]
}}
"""

    response = client.chat.completions.create(
        model="gpt-4-turbo",
        messages=[{"role": "user", "content": prompt}],
        response_format={"type": "json_object"}
    )

    plan = json.loads(response.choices[0].message.content)
    return plan["steps"]

# 例
task = "GitHubリポジトリの全Issueを分析して、カテゴリ別のサマリーを作成"
steps = plan_task(task)

# 出力:
# [
#   {"id": 1, "action": "GitHub APIで全Issue取得", "tool": "github_api"},
#   {"id": 2, "action": "Issueをカテゴリ分類", "tool": "llm_classify", "depends_on": [1]},
#   {"id": 3, "action": "サマリーレポート生成", "tool": "llm_summarize", "depends_on": [2]}
# ]
```

---

### 実行と調整

```python
def execute_plan(steps):
    """計画を実行し、必要に応じて調整"""
    results = {}

    for step in steps:
        # 依存関係チェック
        if "depends_on" in step:
            for dep_id in step["depends_on"]:
                if dep_id not in results:
                    print(f"待機中: ステップ{dep_id}の完了を待つ")
                    continue

        # ステップ実行
        try:
            result = execute_step(step)
            results[step["id"]] = result
            print(f"✅ ステップ{step['id']}完了: {step['action']}")

        except Exception as e:
            print(f"❌ ステップ{step['id']}失敗: {e}")

            # 再計画
            print("計画を調整中...")
            alternative_steps = replan(step, e)
            steps.extend(alternative_steps)

    return results
```

---

## マルチエージェント

### エージェントの役割分担

```
┌─────────────────────────────────────────┐
│      マルチエージェントシステム          │
├─────────────────────────────────────────┤
│                                         │
│  Orchestrator（オーケストレーター）     │
│       ↓        ↓        ↓               │
│    Agent1   Agent2   Agent3             │
│   (Coder)  (Reviewer) (Tester)          │
│                                         │
│  タスク配分 → 実行 → 結果統合           │
└─────────────────────────────────────────┘
```

---

**実装例**:
```python
class CoderAgent:
    def code(self, spec):
        """仕様からコード生成"""
        prompt = f"以下の仕様を実装してください:\n{spec}"
        return llm_generate(prompt)

class ReviewerAgent:
    def review(self, code):
        """コードレビュー"""
        prompt = f"以下のコードをレビューしてください:\n{code}"
        return llm_generate(prompt)

class TesterAgent:
    def test(self, code):
        """テストケース生成"""
        prompt = f"以下のコードのテストを作成してください:\n{code}"
        return llm_generate(prompt)

class Orchestrator:
    def __init__(self):
        self.coder = CoderAgent()
        self.reviewer = ReviewerAgent()
        self.tester = TesterAgent()

    def develop_feature(self, spec):
        """機能開発の全工程を管理"""
        print("1. コード生成中...")
        code = self.coder.code(spec)

        print("2. コードレビュー中...")
        review = self.reviewer.review(code)

        if "改善が必要" in review:
            print("3. コード修正中...")
            code = self.coder.code(f"{spec}\n\nレビュー結果:\n{review}")

        print("4. テスト生成中...")
        tests = self.tester.test(code)

        return {
            "code": code,
            "review": review,
            "tests": tests
        }

# 使用例
orchestrator = Orchestrator()
result = orchestrator.develop_feature("""
ユーザー認証API
- POST /auth/login
- メール・パスワードで認証
- JWTトークンを返す
""")
```

---

## 実用的なAI Agent例

### 1. コード生成エージェント

```python
class CodeGenerationAgent:
    def __init__(self):
        self.tools = {
            "read_file": self.read_file,
            "write_file": self.write_file,
            "run_tests": self.run_tests,
            "search_docs": self.search_docs
        }

    def implement_feature(self, description):
        """機能実装の自動化"""
        plan = [
            "既存コードを調査",
            "類似実装を検索",
            "コード実装",
            "テスト作成",
            "テスト実行",
            "修正（必要なら）"
        ]

        for step in plan:
            print(f"実行中: {step}")
            # ReActループで実行
            ...

        return "実装完了"
```

---

### 2. データ分析エージェント

```python
class DataAnalysisAgent:
    def analyze(self, data_path):
        """データ分析の自動化"""
        # 1. データ読み込み
        df = pd.read_csv(data_path)

        # 2. LLMに分析計画を立てさせる
        plan_prompt = f"""
以下のデータセット概要から分析計画を立ててください:
カラム: {list(df.columns)}
行数: {len(df)}
欠損値: {df.isnull().sum().to_dict()}
"""
        plan = llm_generate(plan_prompt)

        # 3. 計画に従って分析実行
        for step in plan["steps"]:
            if step["type"] == "visualization":
                self.create_plot(df, step["params"])
            elif step["type"] == "statistical_test":
                self.run_statistical_test(df, step["params"])

        # 4. レポート生成
        report = self.generate_report(df, plan)
        return report
```

---

### 3. デバッグエージェント

```python
class DebugAgent:
    def debug(self, error_message, code_context):
        """エラーの自動デバッグ"""
        steps = [
            {"action": "エラー分析", "thought": "エラーメッセージから原因を推測"},
            {"action": "関連コード調査", "tool": "grep"},
            {"action": "StackOverflow検索", "tool": "web_search"},
            {"action": "修正案生成", "thought": "コード修正案を提示"},
            {"action": "修正適用", "tool": "edit_file"},
            {"action": "テスト実行", "tool": "run_tests"}
        ]

        for step in steps:
            result = self.execute_step(step)
            if step["action"] == "テスト実行" and result["status"] == "success":
                return "デバッグ完了"

        return "追加調査が必要"
```

---

## エージェントフレームワーク

### LangChain Agents

```python
from langchain.agents import initialize_agent, AgentType
from langchain.tools import Tool
from langchain.chat_models import ChatOpenAI

# ツール定義
tools = [
    Tool(
        name="Calculator",
        func=lambda x: eval(x),
        description="数式計算に使用"
    ),
    Tool(
        name="Search",
        func=search_api,
        description="Web検索に使用"
    )
]

# LLM
llm = ChatOpenAI(model="gpt-4-turbo", temperature=0)

# エージェント初期化
agent = initialize_agent(
    tools=tools,
    llm=llm,
    agent=AgentType.ZERO_SHOT_REACT_DESCRIPTION,
    verbose=True
)

# 実行
result = agent.run("2024年の世界人口は何人? その数の平方根は?")
```

---

### AutoGen（マルチエージェント）

```python
from autogen import AssistantAgent, UserProxyAgent

# アシスタント（LLM）
assistant = AssistantAgent(
    name="assistant",
    llm_config={"model": "gpt-4-turbo"}
)

# ユーザープロキシ（ツール実行）
user_proxy = UserProxyAgent(
    name="user_proxy",
    human_input_mode="NEVER",
    code_execution_config={"work_dir": "coding"}
)

# 会話開始
user_proxy.initiate_chat(
    assistant,
    message="Pythonでフィボナッチ数列を生成するコードを書いて実行して"
)
```

---

## エージェント開発のベストプラクティス

### 1. エラーハンドリング

```python
def robust_tool_execution(tool, args, max_retries=3):
    """リトライ付きツール実行"""
    for attempt in range(max_retries):
        try:
            result = tool(**args)
            return {"status": "success", "result": result}
        except Exception as e:
            if attempt < max_retries - 1:
                print(f"リトライ {attempt + 1}/{max_retries}: {e}")
                time.sleep(2 ** attempt)  # 指数バックオフ
            else:
                return {"status": "error", "error": str(e)}
```

---

### 2. コスト管理

```python
class CostAwareAgent:
    def __init__(self, budget=10.0):
        self.budget = budget  # USD
        self.spent = 0.0

    def call_llm(self, messages):
        # トークン数推定
        estimated_tokens = sum(len(m["content"]) for m in messages) / 4

        # コスト計算（GPT-4 Turbo）
        estimated_cost = (estimated_tokens / 1000) * 0.01

        if self.spent + estimated_cost > self.budget:
            raise BudgetExceededError("予算超過")

        # LLM呼び出し
        response = client.chat.completions.create(...)

        # 実際のコスト記録
        actual_cost = (
            response.usage.prompt_tokens / 1000 * 0.01 +
            response.usage.completion_tokens / 1000 * 0.03
        )
        self.spent += actual_cost

        return response
```

---

### 3. 安全性

```python
class SafeAgent:
    def __init__(self):
        self.allowed_tools = ["read_file", "search", "calculator"]
        self.forbidden_patterns = [
            r"rm -rf",
            r"DROP TABLE",
            r"__import__\('os'\)"
        ]

    def validate_action(self, action):
        """危険なアクションをブロック"""
        # ツールチェック
        if action["tool"] not in self.allowed_tools:
            raise SecurityError(f"禁止されたツール: {action['tool']}")

        # パターンマッチング
        for pattern in self.forbidden_patterns:
            if re.search(pattern, str(action["args"])):
                raise SecurityError(f"危険なパターン検出: {pattern}")

        return True
```

---

## まとめ

### AI Agentの利点と課題

```
┌─────────────────────────────────────────┐
│          利点                            │
├─────────────────────────────────────────┤
│  ✅ 複雑タスクの自動化                   │
│  ✅ 自律的な問題解決                     │
│  ✅ ツール統合による拡張性               │
│  ✅ 長期的なタスク実行                   │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│          課題                            │
├─────────────────────────────────────────┤
│  ⚠️  予測不可能性（ハルシネーション）    │
│  ⚠️  高コスト（多数のLLM呼び出し）       │
│  ⚠️  セキュリティリスク                  │
│  ⚠️  デバッグの困難さ                    │
└─────────────────────────────────────────┘
```

### 適用領域

| 領域 | ユースケース |
|------|-------------|
| **開発支援** | コード生成、レビュー、デバッグ |
| **データ分析** | 探索的分析、レポート生成 |
| **顧客サポート** | 問い合わせ対応、チケット管理 |
| **リサーチ** | 文献調査、要約 |
| **業務自動化** | ドキュメント作成、スケジュール管理 |

---

## 次のステップ

- [06-modern-ai-tools.md](./06-modern-ai-tools.md) - エージェント開発ツール比較
- [Claude Agent SDK Documentation](https://github.com/anthropics/anthropic-sdk-python) - Anthropic公式SDK

---

## 参考リンク

- [ReAct論文](https://arxiv.org/abs/2210.03629) - Reasoning + Acting
- [LangChain Agents](https://python.langchain.com/docs/modules/agents/)
- [AutoGen Framework](https://microsoft.github.io/autogen/)
- [OpenAI Function Calling](https://platform.openai.com/docs/guides/function-calling)
