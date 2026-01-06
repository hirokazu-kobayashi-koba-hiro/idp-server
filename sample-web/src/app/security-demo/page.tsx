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
  StepContent,
  Divider,
  Chip,
  CircularProgress,
  Paper,
} from "@mui/material";
import { useState } from "react";
import SecurityIcon from "@mui/icons-material/Security";
import WarningIcon from "@mui/icons-material/Warning";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import BlockIcon from "@mui/icons-material/Block";
import PlayArrowIcon from "@mui/icons-material/PlayArrow";
import RestartAltIcon from "@mui/icons-material/RestartAlt";

interface SimulationStep {
  step: number;
  title: string;
  description: string;
  result: "success" | "blocked" | "error";
  details: Record<string, unknown>;
}

interface SimulationResult {
  success: boolean;
  attackBlocked?: boolean;
  summary?: string;
  steps?: SimulationStep[];
  error?: string;
  message?: string;
}

export default function SecurityDemoPage() {
  const [isRunning, setIsRunning] = useState(false);
  const [result, setResult] = useState<SimulationResult | null>(null);

  const runSimulation = async () => {
    setIsRunning(true);
    setResult(null);

    try {
      const response = await fetch("/api/security-test/auth-session?action=simulate-attack");
      const data = await response.json();
      setResult(data);
    } catch (error) {
      setResult({
        success: false,
        error: "シミュレーション実行中にエラーが発生しました",
        message: error instanceof Error ? error.message : String(error),
      });
    } finally {
      setIsRunning(false);
    }
  };

  const resetSimulation = () => {
    setResult(null);
  };

  const getStepIcon = (stepResult: string) => {
    switch (stepResult) {
      case "success":
        return <CheckCircleIcon sx={{ color: "success.main" }} />;
      case "blocked":
        return <BlockIcon sx={{ color: "error.main" }} />;
      case "error":
        return <WarningIcon sx={{ color: "warning.main" }} />;
      default:
        return null;
    }
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
              認可フロー乗っ取り攻撃の防止機能をサーバーサイドでシミュレートします
            </Typography>
          </Box>
        </Stack>

        {/* 攻撃シナリオの説明 */}
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              <WarningIcon sx={{ mr: 1, color: "warning.main", verticalAlign: "middle" }} />
              攻撃シナリオ
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              AUTH_SESSION Cookie がない場合、以下の攻撃が可能になります：
            </Typography>
            <Box sx={{ pl: 2 }}>
              <Stepper orientation="vertical">
                <Step active completed={false}>
                  <StepLabel>
                    <Typography variant="body2">
                      <Chip size="small" label="攻撃者" color="error" sx={{ mr: 1 }} />
                      認可リクエストを開始 → authorization_id を取得
                    </Typography>
                  </StepLabel>
                </Step>
                <Step active completed={false}>
                  <StepLabel>
                    <Typography variant="body2">
                      <Chip size="small" label="攻撃者" color="error" sx={{ mr: 1 }} />
                      認可URLを被害者に送信（フィッシングなど）
                    </Typography>
                  </StepLabel>
                </Step>
                <Step active completed={false}>
                  <StepLabel>
                    <Typography variant="body2">
                      <Chip size="small" label="被害者" color="warning" sx={{ mr: 1 }} />
                      そのURLでログイン（自分の認証情報を入力）
                    </Typography>
                  </StepLabel>
                </Step>
                <Step active completed={false}>
                  <StepLabel>
                    <Typography variant="body2" color="error">
                      <Chip size="small" label="攻撃者" color="error" sx={{ mr: 1 }} />
                      被害者のアカウントでログイン成功 ← これを防ぎたい
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
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              認可リクエスト開始時にブラウザにCookieを設定し、以降の操作で検証します：
            </Typography>
            <Box sx={{ display: "flex", gap: 2, flexWrap: "wrap" }}>
              <Chip
                icon={<SecurityIcon />}
                label="HttpOnly"
                color="primary"
                variant="outlined"
              />
              <Chip
                icon={<SecurityIcon />}
                label="Secure"
                color="primary"
                variant="outlined"
              />
              <Chip
                icon={<SecurityIcon />}
                label="SameSite=Lax"
                color="primary"
                variant="outlined"
              />
            </Box>
            <Typography variant="body2" sx={{ mt: 2 }}>
              被害者は攻撃者のCookieを持っていないため、認証時に <strong>401 Unauthorized</strong> でブロックされます。
            </Typography>
          </CardContent>
        </Card>

        <Divider />

        {/* シミュレーション実行 */}
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              攻撃シミュレーション
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
              サーバーサイドで攻撃シナリオをシミュレートし、AUTH_SESSION Cookieによる保護が機能することを確認します。
            </Typography>

            <Stack direction="row" spacing={2} sx={{ mb: 3 }}>
              <Button
                variant="contained"
                color="primary"
                onClick={runSimulation}
                disabled={isRunning}
                startIcon={isRunning ? <CircularProgress size={20} /> : <PlayArrowIcon />}
              >
                {isRunning ? "実行中..." : "シミュレーション実行"}
              </Button>
              {result && (
                <Button
                  variant="outlined"
                  onClick={resetSimulation}
                  startIcon={<RestartAltIcon />}
                >
                  リセット
                </Button>
              )}
            </Stack>

            {/* 結果表示 */}
            {result && (
              <Stack spacing={3}>
                {/* サマリー */}
                {result.attackBlocked !== undefined && (
                  <Alert severity={result.attackBlocked ? "success" : "error"}>
                    <AlertTitle>
                      {result.attackBlocked ? "攻撃がブロックされました" : "警告: 攻撃がブロックされませんでした"}
                    </AlertTitle>
                    {result.summary}
                  </Alert>
                )}

                {result.error && (
                  <Alert severity="error">
                    <AlertTitle>エラー</AlertTitle>
                    {result.error}: {result.message}
                  </Alert>
                )}

                {/* ステップ詳細 */}
                {result.steps && result.steps.length > 0 && (
                  <Paper variant="outlined" sx={{ p: 2 }}>
                    <Typography variant="subtitle1" fontWeight="bold" gutterBottom>
                      実行ステップ詳細
                    </Typography>
                    <Stepper orientation="vertical">
                      {result.steps.map((step) => (
                        <Step key={step.step} active completed>
                          <StepLabel
                            icon={getStepIcon(step.result)}
                            optional={
                              <Typography variant="caption" color="text.secondary">
                                {step.result === "blocked" ? "ブロック" : step.result}
                              </Typography>
                            }
                          >
                            {step.title}
                          </StepLabel>
                          <StepContent>
                            <Typography variant="body2" sx={{ mb: 1 }}>
                              {step.description}
                            </Typography>
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
                                {JSON.stringify(step.details, null, 2)}
                              </pre>
                            </Paper>
                          </StepContent>
                        </Step>
                      ))}
                    </Stepper>
                  </Paper>
                )}
              </Stack>
            )}
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
