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
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  CircularProgress,
} from "@mui/material";
import { useState, useEffect } from "react";
import FingerprintIcon from "@mui/icons-material/Fingerprint";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import ErrorIcon from "@mui/icons-material/Error";
import InfoIcon from "@mui/icons-material/Info";
import LanguageIcon from "@mui/icons-material/Language";
import PlayArrowIcon from "@mui/icons-material/PlayArrow";
import RestartAltIcon from "@mui/icons-material/RestartAlt";

/**
 * Convert Base64URL string to ArrayBuffer
 */
const base64UrlToBuffer = (base64url: string): ArrayBuffer => {
  const base64 = base64url.replace(/-/g, "+").replace(/_/g, "/");
  const binaryString = atob(base64);
  const bytes = Uint8Array.from(binaryString, (char) => char.charCodeAt(0));
  return bytes.buffer as ArrayBuffer;
};

interface Credential {
  id: string;
  type: string;
  transports?: AuthenticatorTransport[];
}

interface ChallengeResponse {
  challenge: string;
  timeout?: number;
  rpId?: string;
  rp?: {
    id?: string;
    name?: string;
  };
  allowCredentials?: Credential[];
  userVerification?: UserVerificationRequirement;
}

interface TestResult {
  step: string;
  success: boolean;
  message: string;
  details?: Record<string, unknown>;
}

export default function Fido2RpIdDemoPage() {
  const [currentOrigin, setCurrentOrigin] = useState<string>("");
  const [currentHostname, setCurrentHostname] = useState<string>("");
  const [username, setUsername] = useState<string>("");

  // Test state
  const [isRunning, setIsRunning] = useState(false);
  const [authorizationId, setAuthorizationId] = useState<string | null>(null);
  const [challengeData, setChallengeData] = useState<ChallengeResponse | null>(null);
  const [testResults, setTestResults] = useState<TestResult[]>([]);
  const [testMode, setTestMode] = useState<"with-rpid" | "without-rpid" | null>(null);

  useEffect(() => {
    if (typeof window !== "undefined") {
      setCurrentOrigin(window.location.origin);
      setCurrentHostname(window.location.hostname);
    }
  }, []);

  // rpIdの有効性を判定（WebAuthn仕様に基づく）
  const isValidRpId = (rpId: string, hostname: string): boolean => {
    if (rpId === hostname) return true;
    if (hostname.endsWith("." + rpId)) return true;
    return false;
  };

  // サンプルのrpId候補
  const rpIdExamples = [
    { rpId: "local.dev", description: "親ドメイン（推奨）" },
    { rpId: currentHostname, description: "完全一致" },
    { rpId: "other.local.dev", description: "兄弟ドメイン" },
    { rpId: "example.com", description: "無関係なドメイン" },
  ];

  const addResult = (result: TestResult) => {
    setTestResults((prev) => [...prev, result]);
  };

  const resetTest = () => {
    setAuthorizationId(null);
    setChallengeData(null);
    setTestResults([]);
    setTestMode(null);
  };

  // Step 1: 認可リクエストを開始（API route経由でCookieを転送）
  const startAuthorization = async () => {
    setIsRunning(true);
    setTestResults([]);

    try {
      // API route経由でIDPサーバーにリクエスト
      // API routeがSet-Cookieヘッダーをブラウザに転送
      const response = await fetch("/api/fido2-rpid-test?action=start", {
        credentials: "include", // Cookieを受け取る
      });
      const data = await response.json();

      if (data.success && data.authorizationId) {
        setAuthorizationId(data.authorizationId);
        addResult({
          step: "認可リクエスト開始",
          success: true,
          message: `authorization_id を取得し、AUTH_SESSION Cookie がブラウザに設定されました`,
          details: {
            authorizationId: data.authorizationId,
            tenantId: data.tenantId,
          },
        });
      } else {
        addResult({
          step: "認可リクエスト開始",
          success: false,
          message: "認可リクエストの開始に失敗しました",
          details: data,
        });
      }
    } catch (error) {
      addResult({
        step: "認可リクエスト開始",
        success: false,
        message: error instanceof Error ? error.message : "エラーが発生しました",
      });
    } finally {
      setIsRunning(false);
    }
  };

  // Step 2: FIDO2チャレンジを取得（API route経由でCookieを転送）
  const getChallenge = async () => {
    if (!authorizationId) return;

    setIsRunning(true);

    try {
      // API route経由でIDPサーバーにリクエスト（Cookieを転送）
      const response = await fetch(
        `/api/fido2-rpid-test?action=challenge&authorizationId=${authorizationId}&username=${encodeURIComponent(username)}`,
        {
          credentials: "include", // AUTH_SESSION Cookieを送信
        }
      );

      const data = await response.json();

      if (!data.success) {
        addResult({
          step: "FIDO2チャレンジ取得",
          success: false,
          message: `チャレンジの取得に失敗しました (${data.status || response.status})`,
          details: {
            status: data.status || response.status,
            error: data.error,
          },
        });
        return;
      }

      const challengeResponse: ChallengeResponse = data.challenge;
      setChallengeData(challengeResponse);
      const rpId = challengeResponse.rpId || challengeResponse.rp?.id;

      addResult({
        step: "FIDO2チャレンジ取得",
        success: true,
        message: `チャレンジを取得しました (rpId: ${rpId || "なし"})`,
        details: {
          challenge: challengeResponse.challenge?.substring(0, 20) + "...",
          rpId: rpId,
          allowCredentials: challengeResponse.allowCredentials?.length || 0,
          hasTransports: challengeResponse.allowCredentials?.some((c) => (c.transports?.length ?? 0) > 0),
        },
      });
    } catch (error) {
      addResult({
        step: "FIDO2チャレンジ取得",
        success: false,
        message: error instanceof Error ? error.message : "エラーが発生しました",
      });
    } finally {
      setIsRunning(false);
    }
  };

  // Step 3: FIDO2認証を実行（rpIdあり/なし）
  const executeAuthentication = async (withRpId: boolean) => {
    if (!challengeData) return;

    setTestMode(withRpId ? "with-rpid" : "without-rpid");
    setIsRunning(true);

    try {
      const {
        challenge,
        timeout = 60000,
        rpId,
        rp,
        allowCredentials = [],
        userVerification = "required",
      } = challengeData;

      // rpIdを取得（サーバーから返された値）
      const serverRpId = rpId || rp?.id;

      // PublicKeyCredentialRequestOptionsを構築
      const publicKeyOptions: PublicKeyCredentialRequestOptions = {
        challenge: base64UrlToBuffer(challenge),
        timeout,
        userVerification,
      };

      // rpIdを設定（withRpId=true の場合のみ）
      if (withRpId && serverRpId) {
        publicKeyOptions.rpId = serverRpId;
        addResult({
          step: `認証実行 (rpId: ${serverRpId})`,
          success: true,
          message: "rpId を指定して認証を開始します",
          details: { rpId: serverRpId },
        });
      } else {
        addResult({
          step: "認証実行 (rpId: なし)",
          success: true,
          message: "rpId を指定せずに認証を開始します（ブラウザがoriginのhostnameを使用）",
          details: { browserWillUse: currentHostname },
        });
      }

      // allowCredentialsを設定
      if (allowCredentials.length > 0) {
        publicKeyOptions.allowCredentials = allowCredentials.map((cred) => {
          const descriptor: PublicKeyCredentialDescriptor = {
            type: cred.type as PublicKeyCredentialType,
            id: base64UrlToBuffer(cred.id),
          };
          if (cred.transports && cred.transports.length > 0) {
            descriptor.transports = cred.transports;
          }
          return descriptor;
        });
      }

      // WebAuthn認証を実行
      const credential = await navigator.credentials.get({
        publicKey: publicKeyOptions,
        mediation: "optional",
      });

      if (credential) {
        addResult({
          step: "認証成功",
          success: true,
          message: withRpId
            ? "Touch ID で認証が成功しました！rpId 指定により正しい Credential が見つかりました。"
            : "認証が成功しました。",
          details: {
            credentialId: credential.id,
            type: credential.type,
          },
        });
      }
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : "エラーが発生しました";
      const errorName = error instanceof Error ? error.name : "Unknown";

      addResult({
        step: "認証失敗",
        success: false,
        message: withRpId
          ? `認証に失敗しました: ${errorMessage}`
          : `rpId なしでは Credential が見つからず、Touch ID が表示されませんでした（QRコードのみ表示）`,
        details: {
          errorName,
          errorMessage,
          reason: !withRpId
            ? "ブラウザが現在のhostname を rpId として使用したため、登録時の rpId と不一致"
            : undefined,
        },
      });
    } finally {
      setIsRunning(false);
    }
  };

  return (
    <Container maxWidth="lg" sx={{ py: 4 }}>
      <Stack spacing={4}>
        {/* Header */}
        <Stack direction="row" alignItems="center" spacing={2}>
          <FingerprintIcon sx={{ fontSize: 40, color: "primary.main" }} />
          <Box>
            <Typography variant="h4" component="h1">
              FIDO2 rpId サブドメインデプロイ デモ
            </Typography>
            <Typography variant="body2" color="text.secondary">
              rpId設定とサブドメインの関係を実際に確認するためのデモページ
            </Typography>
          </Box>
        </Stack>

        {/* 現在の環境情報 */}
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              <LanguageIcon sx={{ mr: 1, verticalAlign: "middle" }} />
              現在の環境
            </Typography>
            <TableContainer>
              <Table size="small">
                <TableBody>
                  <TableRow>
                    <TableCell component="th" sx={{ fontWeight: "bold", width: 150 }}>
                      Origin
                    </TableCell>
                    <TableCell>
                      <code>{currentOrigin}</code>
                    </TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell component="th" sx={{ fontWeight: "bold" }}>
                      Hostname
                    </TableCell>
                    <TableCell>
                      <code>{currentHostname}</code>
                    </TableCell>
                  </TableRow>
                </TableBody>
              </Table>
            </TableContainer>
          </CardContent>
        </Card>

        {/* rpIdの基本ルール */}
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              <InfoIcon sx={{ mr: 1, color: "info.main", verticalAlign: "middle" }} />
              rpId の基本ルール
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              WebAuthn仕様では、rpIdは以下の条件を満たす必要があります：
            </Typography>
            <Box sx={{ pl: 2, mb: 2 }}>
              <ul>
                <li>
                  <Typography variant="body2">
                    <strong>ルール1:</strong> rpIdは現在のオリジンのドメインと<strong>完全一致</strong>するか、
                    その<strong>親ドメイン（registrable domain suffix）</strong>である必要がある
                  </Typography>
                </li>
                <li>
                  <Typography variant="body2">
                    <strong>ルール2:</strong> rpIdを省略した場合、ブラウザは現在のオリジンのドメインを使用
                  </Typography>
                </li>
                <li>
                  <Typography variant="body2">
                    <strong>ルール3:</strong> 登録時と認証時で同じrpIdを使用する必要がある
                  </Typography>
                </li>
              </ul>
            </Box>
            <Alert severity="warning" sx={{ mb: 2 }}>
              <AlertTitle>重要</AlertTitle>
              サブドメインデプロイでは、認証チャレンジのレスポンスに<strong>rpId</strong>を明示的に含める必要があります。
              省略するとブラウザがオリジンのドメイン（例: sample.local.dev）を使用し、
              登録時のrpId（例: local.dev）と不一致になります。
            </Alert>
          </CardContent>
        </Card>

        {/* rpIdの有効性チェック */}
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              rpId 有効性チェック
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              現在のホスト名 <code>{currentHostname}</code> に対する各rpIdの有効性：
            </Typography>
            <TableContainer component={Paper} variant="outlined">
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>rpId</TableCell>
                    <TableCell>説明</TableCell>
                    <TableCell>有効性</TableCell>
                    <TableCell>理由</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rpIdExamples.map((example) => {
                    const valid = isValidRpId(example.rpId, currentHostname);
                    return (
                      <TableRow key={example.rpId}>
                        <TableCell>
                          <code>{example.rpId}</code>
                        </TableCell>
                        <TableCell>{example.description}</TableCell>
                        <TableCell>
                          {valid ? (
                            <Chip
                              icon={<CheckCircleIcon />}
                              label="有効"
                              color="success"
                              size="small"
                            />
                          ) : (
                            <Chip
                              icon={<ErrorIcon />}
                              label="無効"
                              color="error"
                              size="small"
                            />
                          )}
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2" color="text.secondary">
                            {valid
                              ? example.rpId === currentHostname
                                ? "完全一致"
                                : `${currentHostname} は ${example.rpId} のサブドメイン`
                              : `${currentHostname} は ${example.rpId} と無関係`}
                          </Typography>
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          </CardContent>
        </Card>

        <Divider />

        {/* 実際のテスト */}
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              <PlayArrowIcon sx={{ mr: 1, verticalAlign: "middle" }} />
              rpId 動作確認テスト
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
              実際に認可フローを開始し、rpId指定あり/なしでFIDO2認証の挙動を確認します。
            </Typography>

            <Alert severity="info" sx={{ mb: 3 }}>
              <AlertTitle>前提条件</AlertTitle>
              このテストを実行するには、事前にPasskeyが登録されている必要があります。
              登録時のrpIdは親ドメイン（例: local.dev）である必要があります。
            </Alert>

            <Stack spacing={3}>
              {/* Step 1: 認可リクエスト開始 */}
              <Box>
                <Typography variant="subtitle2" fontWeight="bold" gutterBottom>
                  Step 1: 認可リクエストを開始
                </Typography>
                <Button
                  variant="contained"
                  onClick={startAuthorization}
                  disabled={isRunning || !!authorizationId}
                  startIcon={isRunning ? <CircularProgress size={20} /> : <PlayArrowIcon />}
                >
                  認可リクエスト開始
                </Button>
                {authorizationId && (
                  <Chip
                    label={`authorization_id: ${authorizationId.substring(0, 8)}...`}
                    color="success"
                    size="small"
                    sx={{ ml: 2 }}
                  />
                )}
              </Box>

              {/* Step 2: ユーザー名入力とチャレンジ取得 */}
              {authorizationId && (
                <Box>
                  <Typography variant="subtitle2" fontWeight="bold" gutterBottom>
                    Step 2: FIDO2チャレンジを取得
                  </Typography>
                  <Stack direction="row" spacing={2} alignItems="center">
                    <TextField
                      label="Username（任意）"
                      value={username}
                      onChange={(e) => setUsername(e.target.value)}
                      size="small"
                      sx={{ width: 300 }}
                      placeholder="空欄でDiscoverable Credential"
                    />
                    <Button
                      variant="contained"
                      onClick={getChallenge}
                      disabled={isRunning || !!challengeData}
                      startIcon={isRunning ? <CircularProgress size={20} /> : <PlayArrowIcon />}
                    >
                      チャレンジ取得
                    </Button>
                  </Stack>
                </Box>
              )}

              {/* Step 3: 認証実行（rpIdあり/なし） */}
              {challengeData && (
                <Box>
                  <Typography variant="subtitle2" fontWeight="bold" gutterBottom>
                    Step 3: FIDO2認証を実行
                  </Typography>
                  <Stack direction="row" spacing={2}>
                    <Button
                      variant="contained"
                      color="success"
                      onClick={() => executeAuthentication(true)}
                      disabled={isRunning}
                      startIcon={isRunning && testMode === "with-rpid" ? <CircularProgress size={20} /> : <FingerprintIcon />}
                    >
                      rpId 指定あり（Touch ID表示）
                    </Button>
                    <Button
                      variant="contained"
                      color="warning"
                      onClick={() => executeAuthentication(false)}
                      disabled={isRunning}
                      startIcon={isRunning && testMode === "without-rpid" ? <CircularProgress size={20} /> : <FingerprintIcon />}
                    >
                      rpId 指定なし（QRコードのみ）
                    </Button>
                  </Stack>
                </Box>
              )}

              {/* リセットボタン */}
              {testResults.length > 0 && (
                <Box>
                  <Button
                    variant="outlined"
                    onClick={resetTest}
                    startIcon={<RestartAltIcon />}
                  >
                    テストをリセット
                  </Button>
                </Box>
              )}
            </Stack>

            {/* テスト結果 */}
            {testResults.length > 0 && (
              <Paper variant="outlined" sx={{ p: 2, mt: 3 }}>
                <Typography variant="subtitle1" fontWeight="bold" gutterBottom>
                  実行ログ
                </Typography>
                <Stepper orientation="vertical">
                  {testResults.map((result, index) => (
                    <Step key={index} active completed>
                      <StepLabel
                        icon={
                          result.success ? (
                            <CheckCircleIcon sx={{ color: "success.main" }} />
                          ) : (
                            <ErrorIcon sx={{ color: "error.main" }} />
                          )
                        }
                      >
                        <Typography variant="body2" fontWeight="bold">
                          {result.step}
                        </Typography>
                      </StepLabel>
                      <Box sx={{ pl: 4, pb: 2 }}>
                        <Typography variant="body2" sx={{ mb: 1 }}>
                          {result.message}
                        </Typography>
                        {result.details && (
                          <Paper
                            sx={{
                              p: 1,
                              bgcolor: "grey.100",
                              fontSize: "0.75rem",
                              fontFamily: "monospace",
                              overflow: "auto",
                            }}
                          >
                            <pre style={{ margin: 0 }}>
                              {JSON.stringify(result.details, null, 2)}
                            </pre>
                          </Paper>
                        )}
                      </Box>
                    </Step>
                  ))}
                </Stepper>
              </Paper>
            )}
          </CardContent>
        </Card>

        {/* サブドメインデプロイのシナリオ */}
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              サブドメインデプロイのシナリオ
            </Typography>
            <Box sx={{ mb: 3 }}>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                以下のようなサブドメイン構成を想定：
              </Typography>
              <Paper variant="outlined" sx={{ p: 2, bgcolor: "grey.50" }}>
                <pre style={{ margin: 0, fontSize: "0.875rem" }}>
{`API:    https://api.local.dev      (認可エンドポイント)
認証UI: https://auth.local.dev     (ログイン画面)
Web:    https://sample.local.dev   (アプリケーション)`}
                </pre>
              </Paper>
            </Box>

            <Divider sx={{ my: 2 }} />

            {/* 問題のシナリオ */}
            <Typography variant="subtitle1" fontWeight="bold" color="error.main" gutterBottom>
              問題のシナリオ（rpId不一致）
            </Typography>
            <Stepper orientation="vertical" sx={{ mb: 3 }}>
              <Step active completed={false}>
                <StepLabel>
                  <Typography variant="body2">
                    <Chip size="small" label="登録時" color="primary" sx={{ mr: 1 }} />
                    auth.local.dev で Passkey 登録 (rpId: &quot;local.dev&quot;)
                  </Typography>
                </StepLabel>
              </Step>
              <Step active completed={false}>
                <StepLabel>
                  <Typography variant="body2">
                    <Chip size="small" label="認証時" color="warning" sx={{ mr: 1 }} />
                    サーバーが rpId を返さない（または rp.id のみ返す）
                  </Typography>
                </StepLabel>
              </Step>
              <Step active completed={false}>
                <StepLabel>
                  <Typography variant="body2">
                    <Chip size="small" label="ブラウザ" color="secondary" sx={{ mr: 1 }} />
                    rpId が省略されているため &quot;sample.local.dev&quot; を使用
                  </Typography>
                </StepLabel>
              </Step>
              <Step active completed={false}>
                <StepLabel>
                  <Typography variant="body2" color="error">
                    <Chip size="small" label="結果" color="error" sx={{ mr: 1 }} />
                    rpId 不一致 → Credential が見つからない → Touch ID が表示されない
                  </Typography>
                </StepLabel>
              </Step>
            </Stepper>

            {/* 正しいシナリオ */}
            <Typography variant="subtitle1" fontWeight="bold" color="success.main" gutterBottom>
              正しいシナリオ（rpId一致）
            </Typography>
            <Stepper orientation="vertical">
              <Step active completed={false}>
                <StepLabel>
                  <Typography variant="body2">
                    <Chip size="small" label="登録時" color="primary" sx={{ mr: 1 }} />
                    auth.local.dev で Passkey 登録 (rpId: &quot;local.dev&quot;)
                  </Typography>
                </StepLabel>
              </Step>
              <Step active completed={false}>
                <StepLabel>
                  <Typography variant="body2">
                    <Chip size="small" label="認証時" color="primary" sx={{ mr: 1 }} />
                    サーバーが rpId: &quot;local.dev&quot; を明示的に返す
                  </Typography>
                </StepLabel>
              </Step>
              <Step active completed={false}>
                <StepLabel>
                  <Typography variant="body2">
                    <Chip size="small" label="ブラウザ" color="secondary" sx={{ mr: 1 }} />
                    指定された rpId: &quot;local.dev&quot; を使用
                  </Typography>
                </StepLabel>
              </Step>
              <Step active completed={false}>
                <StepLabel>
                  <Typography variant="body2" color="success.main">
                    <Chip size="small" label="結果" color="success" sx={{ mr: 1 }} />
                    rpId 一致 → Credential が見つかる → Touch ID が表示される
                  </Typography>
                </StepLabel>
              </Step>
            </Stepper>
          </CardContent>
        </Card>

        {/* 実装のポイント */}
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              実装のポイント
            </Typography>

            <Typography variant="subtitle2" fontWeight="bold" sx={{ mt: 2, mb: 1 }}>
              1. サーバーサイド（認証チャレンジレスポンス）
            </Typography>
            <Paper variant="outlined" sx={{ p: 2, bgcolor: "grey.50", mb: 2 }}>
              <pre style={{ margin: 0, fontSize: "0.75rem", overflow: "auto" }}>
{`// 認証チャレンジのレスポンスに rpId を含める
{
  "challenge": "...",
  "rpId": "local.dev",  // または rp.id でネスト
  "allowCredentials": [
    {
      "type": "public-key",
      "id": "credential_id",
      "transports": ["internal", "hybrid"]
    }
  ],
  "userVerification": "required"
}`}
              </pre>
            </Paper>

            <Typography variant="subtitle2" fontWeight="bold" sx={{ mt: 2, mb: 1 }}>
              2. フロントエンド（rpIdの取得）
            </Typography>
            <Paper variant="outlined" sx={{ p: 2, bgcolor: "grey.50", mb: 2 }}>
              <pre style={{ margin: 0, fontSize: "0.75rem", overflow: "auto" }}>
{`// rpId をフラット形式またはネスト形式から取得
const rpId = response.rpId || response.rp?.id;

const publicKeyOptions = {
  challenge: base64UrlToBuffer(challenge),
  rpId: rpId,  // 明示的に設定
  allowCredentials: [...],
  userVerification: "required"
};`}
              </pre>
            </Paper>

            <Typography variant="subtitle2" fontWeight="bold" sx={{ mt: 2, mb: 1 }}>
              3. transports の重要性
            </Typography>
            <Alert severity="info">
              <AlertTitle>transports を含めること</AlertTitle>
              <code>allowCredentials</code> に <code>transports: [&quot;internal&quot;, &quot;hybrid&quot;]</code> を含めると、
              ブラウザが適切な認証器（Touch ID等）を優先表示します。省略するとQRコードのみ表示される場合があります。
            </Alert>
          </CardContent>
        </Card>

        {/* 関連ドキュメント */}
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              関連ドキュメント
            </Typography>
            <Typography variant="body2" component="div">
              <ul>
                <li>
                  <a href="https://www.w3.org/TR/webauthn-2/#relying-party-identifier" target="_blank" rel="noopener noreferrer">
                    W3C WebAuthn Level 2 - Relying Party Identifier
                  </a>
                </li>
                <li>
                  <a href="https://www.w3.org/TR/webauthn-2/#dom-publickeycredentialdescriptor-transports" target="_blank" rel="noopener noreferrer">
                    W3C WebAuthn Level 2 - transports
                  </a>
                </li>
                <li>FIDO2/WebAuthn FAQ・トラブルシューティング（社内ドキュメント）</li>
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
