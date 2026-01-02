"use client";

import {
  Container,
  Stack,
  Typography,
  Card,
  CardContent,
  Button,
  Alert,
  AlertTitle,
  Box,
  Stepper,
  Step,
  StepLabel,
  Divider,
  Chip,
  TextField,
  Paper,
} from "@mui/material";
import { useState } from "react";
import SecurityIcon from "@mui/icons-material/Security";
import WarningIcon from "@mui/icons-material/Warning";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import ContentCopyIcon from "@mui/icons-material/ContentCopy";

const issuer = process.env.NEXT_PUBLIC_IDP_SERVER_ISSUER;
const clientId = process.env.NEXT_PUBLIC_IDP_CLIENT_ID;
const frontendUrl = process.env.NEXT_PUBLIC_FRONTEND_URL;

export default function SecurityDemoPage() {
  const [attackerAuthUrl, setAttackerAuthUrl] = useState<string>("");
  const [attackStep, setAttackStep] = useState(0);
  const [attackResult, setAttackResult] = useState<"success" | "blocked" | null>(null);
  const [copiedUrl, setCopiedUrl] = useState(false);

  // Step 1: 攻撃者が認可リクエストを開始
  const startAttackerAuth = async () => {
    const state = generateRandomState();
    const codeVerifier = generateCodeVerifier();
    const codeChallenge = await generateCodeChallenge(codeVerifier);

    // 認可URLを構築
    const authUrl = new URL(`${issuer}/v1/authorizations`);
    authUrl.searchParams.set("client_id", clientId || "");
    authUrl.searchParams.set("response_type", "code");
    authUrl.searchParams.set("scope", "openid profile email");
    authUrl.searchParams.set("redirect_uri", `${frontendUrl}/api/security-test/callback`);
    authUrl.searchParams.set("state", state);
    authUrl.searchParams.set("code_challenge", codeChallenge);
    authUrl.searchParams.set("code_challenge_method", "S256");

    setAttackerAuthUrl(authUrl.toString());
    setAttackStep(1);

    // 攻撃者のブラウザで認可を開始（新しいウィンドウで）
    window.open(authUrl.toString(), "attacker_window", "width=800,height=600");
  };

  // URLをクリップボードにコピー
  const copyAttackUrl = async () => {
    if (attackerAuthUrl) {
      await navigator.clipboard.writeText(attackerAuthUrl);
      setCopiedUrl(true);
      setTimeout(() => setCopiedUrl(false), 2000);
    }
  };

  // Step 3: 被害者として認可URLを開く（別ブラウザ/シークレットモードをシミュレート）
  const openAsVictim = () => {
    if (attackerAuthUrl) {
      // 被害者は攻撃者のCookieを持っていないので、AUTH_SESSION検証で失敗するはず
      setAttackStep(3);
      window.open(attackerAuthUrl, "victim_window", "width=800,height=600");
    }
  };

  // デモをリセット
  const resetDemo = () => {
    setAttackerAuthUrl("");
    setAttackStep(0);
    setAttackResult(null);
  };

  return (
    <Container maxWidth="lg" sx={{ py: 4 }}>
      <Stack spacing={4}>
        {/* Header */}
        <Stack direction="row" alignItems="center" spacing={2}>
          <SecurityIcon sx={{ fontSize: 40, color: "primary.main" }} />
          <Box>
            <Typography variant="h4" component="h1">
              AUTH_SESSION Cookie セキュリティデモ
            </Typography>
            <Typography variant="body2" color="text.secondary">
              認可フロー乗っ取り攻撃（Session Fixation Attack の亜種）の防止機能をテストします
            </Typography>
          </Box>
        </Stack>

        {/* 脆弱性の説明 */}
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              <WarningIcon sx={{ mr: 1, color: "warning.main", verticalAlign: "middle" }} />
              攻撃シナリオ（AUTH_SESSION Cookie がない場合）
            </Typography>
            <Box sx={{ pl: 4, mt: 2 }}>
              <Stepper orientation="vertical">
                <Step active>
                  <StepLabel>
                    <Typography variant="body2">
                      <strong>攻撃者</strong>が認可リクエストを開始 → authorization_id=abc123 を取得
                    </Typography>
                  </StepLabel>
                </Step>
                <Step active>
                  <StepLabel>
                    <Typography variant="body2">
                      <strong>攻撃者</strong>が認可URLを<strong>被害者</strong>に送信
                    </Typography>
                  </StepLabel>
                </Step>
                <Step active>
                  <StepLabel>
                    <Typography variant="body2">
                      <strong>被害者</strong>がそのURLでログイン（被害者の認証情報を入力）
                    </Typography>
                  </StepLabel>
                </Step>
                <Step active>
                  <StepLabel>
                    <Typography variant="body2" color="error">
                      <strong>攻撃者</strong>が authorization_id=abc123 で認可を完了 → 被害者のアカウントでログイン成功
                    </Typography>
                  </StepLabel>
                </Step>
              </Stepper>
            </Box>
          </CardContent>
        </Card>

        {/* 保護メカニズムの説明 */}
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              <CheckCircleIcon sx={{ mr: 1, color: "success.main", verticalAlign: "middle" }} />
              AUTH_SESSION Cookie による保護
            </Typography>
            <Box sx={{ pl: 4, mt: 2 }}>
              <Stepper orientation="vertical">
                <Step active>
                  <StepLabel>
                    <Typography variant="body2">
                      認可リクエスト開始時に <Chip size="small" label="IDP_AUTH_SESSION" color="primary" /> Cookie を設定
                    </Typography>
                  </StepLabel>
                </Step>
                <Step active>
                  <StepLabel>
                    <Typography variant="body2">
                      Cookie の値（authSessionId）を AuthenticationTransaction に保存
                    </Typography>
                  </StepLabel>
                </Step>
                <Step active>
                  <StepLabel>
                    <Typography variant="body2">
                      認証時（interact/authorize）に Cookie と Transaction の authSessionId を照合
                    </Typography>
                  </StepLabel>
                </Step>
                <Step active>
                  <StepLabel>
                    <Typography variant="body2" color="success.main">
                      <strong>不一致の場合は 401 エラー</strong> → 攻撃を阻止
                    </Typography>
                  </StepLabel>
                </Step>
              </Stepper>
            </Box>
          </CardContent>
        </Card>

        <Divider />

        {/* インタラクティブデモ */}
        <Typography variant="h5">インタラクティブデモ</Typography>

        <Alert severity="info">
          <AlertTitle>デモの手順</AlertTitle>
          <ol style={{ margin: 0, paddingLeft: 20 }}>
            <li>「攻撃者として認可を開始」をクリック - 攻撃者のブラウザウィンドウが開きます</li>
            <li>開いたウィンドウでログインは<strong>しないで</strong>ください（攻撃者はログイン画面で止まります）</li>
            <li>生成されたURLをコピーして、別のブラウザまたはシークレットモードで開きます</li>
            <li>被害者としてログインを試みると、AUTH_SESSION検証により拒否されることを確認します</li>
          </ol>
        </Alert>

        <Card>
          <CardContent>
            <Stack spacing={3}>
              {/* Step 1: 攻撃者が認可開始 */}
              <Box>
                <Typography variant="subtitle1" fontWeight="bold" gutterBottom>
                  Step 1: 攻撃者として認可リクエストを開始
                </Typography>
                <Button
                  variant="contained"
                  color="error"
                  onClick={startAttackerAuth}
                  disabled={attackStep > 0}
                  startIcon={<WarningIcon />}
                >
                  攻撃者として認可を開始
                </Button>
              </Box>

              {/* Step 2: 生成されたURL */}
              {attackerAuthUrl && (
                <Box>
                  <Typography variant="subtitle1" fontWeight="bold" gutterBottom>
                    Step 2: 攻撃用URL（被害者に送信するURL）
                  </Typography>
                  <Paper sx={{ p: 2, bgcolor: "grey.100" }}>
                    <TextField
                      fullWidth
                      multiline
                      rows={3}
                      value={attackerAuthUrl}
                      InputProps={{ readOnly: true }}
                      size="small"
                    />
                    <Button
                      sx={{ mt: 1 }}
                      variant="outlined"
                      size="small"
                      onClick={copyAttackUrl}
                      startIcon={<ContentCopyIcon />}
                    >
                      {copiedUrl ? "コピーしました!" : "URLをコピー"}
                    </Button>
                  </Paper>
                  <Alert severity="warning" sx={{ mt: 2 }}>
                    このURLを別のブラウザ（またはシークレットモード）で開いてください。
                    同じブラウザで開くと、攻撃者の AUTH_SESSION Cookie が送信されるため、
                    攻撃が成功してしまいます（これは正常な動作です）。
                  </Alert>
                </Box>
              )}

              {/* Step 3: 被害者として開く */}
              {attackStep >= 1 && (
                <Box>
                  <Typography variant="subtitle1" fontWeight="bold" gutterBottom>
                    Step 3: 被害者としてURLを開く（シミュレーション）
                  </Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                    注意: 実際の攻撃シナリオでは、被害者は別のブラウザセッションからアクセスします。
                    このボタンは同じブラウザで開くため、Cookie共有により攻撃が成功する可能性があります。
                    正確なテストには、シークレットモードまたは別ブラウザを使用してください。
                  </Typography>
                  <Button
                    variant="contained"
                    color="warning"
                    onClick={openAsVictim}
                  >
                    別ウィンドウで開く（被害者シミュレーション）
                  </Button>
                </Box>
              )}

              {/* 結果 */}
              {attackResult === "blocked" && (
                <Alert severity="success">
                  <AlertTitle>攻撃が阻止されました</AlertTitle>
                  AUTH_SESSION Cookie の検証により、別のブラウザセッションからの認可完了が拒否されました。
                </Alert>
              )}

              {attackResult === "success" && (
                <Alert severity="error">
                  <AlertTitle>警告: 攻撃が成功しました</AlertTitle>
                  同じブラウザで開いたため、Cookie が共有されて攻撃が成功しました。
                  実際の保護を確認するには、別のブラウザまたはシークレットモードでテストしてください。
                </Alert>
              )}

              {/* リセット */}
              {attackStep > 0 && (
                <Box>
                  <Button variant="outlined" onClick={resetDemo}>
                    デモをリセット
                  </Button>
                </Box>
              )}
            </Stack>
          </CardContent>
        </Card>

        {/* API テスト */}
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              API テスト（サーバーサイドからの直接テスト）
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              サーバーサイドから直接 API を呼び出して、AUTH_SESSION Cookie がない場合の動作を確認します。
            </Typography>
            <Stack spacing={2}>
              <Button
                variant="contained"
                onClick={async () => {
                  const res = await fetch("/api/security-test/auth-session?action=start");
                  const data = await res.json();
                  alert(JSON.stringify(data, null, 2));
                }}
              >
                認可リクエスト開始テスト
              </Button>
              <Alert severity="info">
                サーバーサイドからの直接呼び出しでは、ブラウザの Cookie が送信されないため、
                interact エンドポイントへのアクセスは AUTH_SESSION 検証により拒否されます。
              </Alert>
            </Stack>
          </CardContent>
        </Card>

        {/* 技術的詳細 */}
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              技術的詳細
            </Typography>
            <Typography variant="body2" component="div">
              <ul>
                <li><strong>Cookie名:</strong> IDP_AUTH_SESSION</li>
                <li><strong>属性:</strong> HttpOnly, Secure, SameSite=Lax</li>
                <li><strong>有効期限:</strong> 認可リクエストの有効期限と同じ</li>
                <li><strong>検証タイミング:</strong> interact(), authorize(), deny(), callbackFederation()</li>
                <li><strong>エラーコード:</strong> auth_session_mismatch (401 Unauthorized)</li>
              </ul>
            </Typography>
          </CardContent>
        </Card>

        {/* 戻るリンク */}
        <Box>
          <Button href="/" variant="text">
            ← ホームに戻る
          </Button>
        </Box>
      </Stack>
    </Container>
  );
}

// ユーティリティ関数
function generateRandomState(): string {
  const array = new Uint8Array(32);
  crypto.getRandomValues(array);
  return Array.from(array, (byte) => byte.toString(16).padStart(2, "0")).join("");
}

function generateCodeVerifier(): string {
  const array = new Uint8Array(32);
  crypto.getRandomValues(array);
  return base64UrlEncode(array);
}

async function generateCodeChallenge(verifier: string): Promise<string> {
  const encoder = new TextEncoder();
  const data = encoder.encode(verifier);
  const digest = await crypto.subtle.digest("SHA-256", data);
  return base64UrlEncode(new Uint8Array(digest));
}

function base64UrlEncode(array: Uint8Array): string {
  const base64 = btoa(String.fromCharCode(...array));
  return base64.replace(/\+/g, "-").replace(/\//g, "_").replace(/=/g, "");
}
