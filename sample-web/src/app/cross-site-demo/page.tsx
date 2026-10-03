"use client";

import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Container,
  Divider,
  Grid,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import CancelIcon from "@mui/icons-material/Cancel";
import LoginIcon from "@mui/icons-material/Login";
import PlayArrowIcon from "@mui/icons-material/PlayArrow";
import Link from "next/link";
import { signIn, useSession } from "next-auth/react";
import { useState } from "react";

/* ------------------------------------------------------------------ *
 * 1. The two topologies, as sequences
 * ------------------------------------------------------------------ */

type Kind = "top-level" | "xhr" | "backchannel";

interface SequenceStep {
  title: string;
  kind: Kind;
  cookie: "届く" | "届かない" | "保存される" | "-";
  what: string;
}

const SAME_SITE: SequenceStep[] = [
  {
    title: "認可リクエスト",
    kind: "top-level",
    cookie: "保存される",
    what: "RP がブラウザを idp-server へ遷移させる。IDP_AUTH_SESSION（このブラウザが始めた印）が保存され、認可画面へ 302。",
  },
  {
    title: "認証（パスワード・サインアップなど）",
    kind: "xhr",
    cookie: "届く",
    what: "認可画面 auth.local.test は idp-server と同じサイト。XHR にも Cookie が付くので、IDP_AUTH_SESSION で「始めたブラウザか」を確かめる。",
  },
  {
    title: "authorize",
    kind: "xhr",
    cookie: "届く",
    what: "code 付きの redirect_uri が返る。OP セッションの Cookie（IDP_IDENTITY）もこの応答で保存される。",
  },
  {
    title: "RP へ戻る",
    kind: "top-level",
    cookie: "-",
    what: "認可画面が redirect_uri へそのまま遷移する。",
  },
];

const CROSS_SITE: SequenceStep[] = [
  {
    title: "認可リクエスト",
    kind: "top-level",
    cookie: "保存される",
    what: "同一サイト構成と同じ。トップレベル遷移なので Safari でも Cookie は first-party として保存される。",
  },
  {
    title: "認証（パスワード・サインアップなど）",
    kind: "xhr",
    cookie: "届かない",
    what: "認可画面 auth.idp.local は別サイト。Safari はこの XHR に Cookie を付けない。代わりに、資格情報を出したブラウザにだけ auth_proof ① を返す。",
  },
  {
    title: "authorize ＋ auth_proof ①",
    kind: "xhr",
    cookie: "届かない",
    what: "① を消費する（使い捨て）。code はボディに出さず、/complete 用の auth_proof ② の中に入れて返す。",
  },
  {
    title: "/complete ＋ auth_proof ②",
    kind: "top-level",
    cookie: "届く",
    what: "idp-server へのトップレベル遷移なので first-party。ここで OP セッションの Cookie を保存し、② の中の遷移先（code 付き）へ 302 する。",
  },
];

const KIND_LABEL: Record<Kind, string> = {
  "top-level": "トップレベル遷移",
  xhr: "XHR",
  backchannel: "サーバー間",
};

const cookieColor = (cookie: string) =>
  cookie === "届かない" ? "error" : cookie === "-" ? "default" : "success";

const SequenceCard = ({
  title,
  host,
  steps,
  accent,
}: {
  title: string;
  host: string;
  steps: SequenceStep[];
  accent: "primary" | "secondary";
}) => (
  <Card variant="outlined" sx={{ height: "100%", borderTop: 3, borderTopColor: `${accent}.main` }}>
    <CardContent>
      <Typography variant="h6" fontWeight="bold">
        {title}
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2, fontFamily: "monospace" }}>
        {host}
      </Typography>
      <Stack spacing={2}>
        {steps.map((step, index) => (
          <Box key={step.title} sx={{ display: "grid", gridTemplateColumns: "28px 1fr", gap: 1 }}>
            <Typography sx={{ fontFamily: "monospace", color: "text.secondary", pt: 0.25 }}>
              {index + 1}
            </Typography>
            <Box>
              <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                <Typography fontWeight="bold">{step.title}</Typography>
                <Chip size="small" variant="outlined" label={KIND_LABEL[step.kind]} />
                {step.cookie !== "-" && (
                  <Chip
                    size="small"
                    color={cookieColor(step.cookie)}
                    label={`Cookie ${step.cookie}`}
                  />
                )}
              </Stack>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                {step.what}
              </Typography>
            </Box>
          </Box>
        ))}
      </Stack>
    </CardContent>
  </Card>
);

/* ------------------------------------------------------------------ *
 * 2. The same sign-in in a real browser
 * ------------------------------------------------------------------ */

const decodeJwt = (jwt?: string): Record<string, unknown> | null => {
  if (!jwt) return null;
  try {
    const payload = jwt.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    return JSON.parse(decodeURIComponent(escape(atob(payload))));
  } catch {
    return null;
  }
};

const formatTime = (seconds: unknown) =>
  typeof seconds === "number" ? new Date(seconds * 1000).toLocaleString("ja-JP") : "-";

const LiveSignIn = () => {
  const { data: session } = useSession();
  const claims = decodeJwt(session?.idToken);
  const crossSite = session?.provider === "idp-server-cross-site";

  return (
    <Stack spacing={2}>
      <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
        <Button
          variant="contained"
          startIcon={<LoginIcon />}
          onClick={() => signIn("idp-server", { callbackUrl: "/cross-site-demo" })}
        >
          同一サイトの認可画面でログイン
        </Button>
        <Button
          variant="contained"
          color="secondary"
          startIcon={<LoginIcon />}
          onClick={() => signIn("idp-server-cross-site", { callbackUrl: "/cross-site-demo" })}
        >
          別サイトの認可画面でログイン
        </Button>
      </Stack>
      <Typography variant="body2" color="text.secondary">
        Safari で開くと差がはっきり出ます。別サイトの認可画面（auth.idp.local）からの XHR には、Safari は
        idp-server の Cookie を付けません。それでもログインが通り、OP セッションが残ることを確かめられます。
      </Typography>

      {session && (
        <Paper variant="outlined" sx={{ p: 2 }}>
          <Stack spacing={1}>
            <Stack direction="row" spacing={1} alignItems="center">
              <Typography fontWeight="bold">いまのログイン</Typography>
              <Chip
                size="small"
                color={crossSite ? "secondary" : "primary"}
                label={crossSite ? "別サイトの認可画面（auth_proof）" : "同一サイトの認可画面（Cookie 束縛）"}
              />
            </Stack>
            <TableContainer>
              <Table size="small">
                <TableBody>
                  <TableRow>
                    <TableCell sx={{ width: 140 }}>sub</TableCell>
                    <TableCell sx={{ fontFamily: "monospace" }}>{String(claims?.sub ?? "-")}</TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell>sid</TableCell>
                    <TableCell sx={{ fontFamily: "monospace" }}>
                      {claims?.sid ? String(claims.sid) : "なし（OP セッションに結びついていない）"}
                    </TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell>auth_time</TableCell>
                    <TableCell>{formatTime(claims?.auth_time)}</TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell>amr</TableCell>
                    <TableCell>{Array.isArray(claims?.amr) ? claims.amr.join(", ") : "-"}</TableCell>
                  </TableRow>
                </TableBody>
              </Table>
            </TableContainer>
            <Typography variant="body2" color="text.secondary">
              ログアウトは <Link href="/home">ダッシュボード</Link> から。
            </Typography>
          </Stack>
        </Paper>
      )}
    </Stack>
  );
};

/* ------------------------------------------------------------------ *
 * 3. Boundaries, replayed step by step
 * ------------------------------------------------------------------ */

interface RunStep {
  title: string;
  kind: Kind;
  request: string;
  cookie: "sent" | "not sent" | "-";
  status: number;
  expected: number;
  ok: boolean;
  note: string;
  details?: Record<string, unknown>;
}

interface RunResult {
  scenario: string;
  ok: boolean;
  steps: RunStep[];
  idToken?: { sub?: string; sid?: string; auth_time?: number; amr?: string[] };
  error?: string;
}

const SCENARIOS: { key: string; label: string; description: string; attack: boolean }[] = [
  {
    key: "same-site",
    label: "正常：同一サイト",
    description: "Cookie で束縛し、authorize の応答で code が返る。",
    attack: false,
  },
  {
    key: "cross-site",
    label: "正常：別サイト",
    description: "認証で proof ①、authorize で proof ②、/complete で code。ID Token に sid が入る。",
    attack: false,
  },
  {
    key: "no-proof",
    label: "auth_proof なしで authorize",
    description: "認可リクエスト ID だけを知っている第三者を想定。",
    attack: true,
  },
  {
    key: "reuse-proof",
    label: "proof ① を使い回す",
    description: "一度使った proof でもう一度 authorize する。",
    attack: true,
  },
  {
    key: "wrong-stage",
    label: "proof ① を /complete に出す",
    description: "authorize を飛ばして code を受け取ろうとする。",
    attack: true,
  },
  {
    key: "complete-twice",
    label: "/complete を二度呼ぶ",
    description: "code をもう一度届けさせようとする。",
    attack: true,
  },
];

const COOKIE_LABEL: Record<RunStep["cookie"], string> = {
  sent: "送る",
  "not sent": "送らない",
  "-": "-",
};

const Replay = () => {
  const [running, setRunning] = useState<string | null>(null);
  const [result, setResult] = useState<RunResult | null>(null);

  const run = async (scenario: string) => {
    setRunning(scenario);
    setResult(null);
    try {
      const response = await fetch("/api/cross-site-demo", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ scenario }),
      });
      setResult(await response.json());
    } catch (error) {
      setResult({ scenario, ok: false, steps: [], error: String(error) });
    } finally {
      setRunning(null);
    }
  };

  const selected = SCENARIOS.find((s) => s.key === result?.scenario);

  return (
    <Stack spacing={2}>
      <Typography variant="body2" color="text.secondary">
        sample-web のサーバーがブラウザの代わりに idp-server を順に呼びます。Cookie の扱いはブラウザに合わせていて、
        トップレベル遷移では IDP_AUTH_SESSION を送り、認可画面からの XHR では同一サイト構成のときだけ送ります（別サイト構成は
        Safari と同じく送らない）。実行のたびにサインアップで新しいユーザーを作ります。
      </Typography>
      <Grid container spacing={1}>
        {SCENARIOS.map((scenario) => (
          <Grid item key={scenario.key} xs={12} sm={6} md={4}>
            <Button
              fullWidth
              variant={result?.scenario === scenario.key ? "contained" : "outlined"}
              color={scenario.attack ? "warning" : "primary"}
              startIcon={running === scenario.key ? <CircularProgress size={16} /> : <PlayArrowIcon />}
              disabled={running !== null}
              onClick={() => run(scenario.key)}
              sx={{ justifyContent: "flex-start", textAlign: "left", height: "100%" }}
            >
              <Box>
                <Typography variant="body2" fontWeight="bold">
                  {scenario.label}
                </Typography>
                <Typography variant="caption" sx={{ display: "block", opacity: 0.8 }}>
                  {scenario.description}
                </Typography>
              </Box>
            </Button>
          </Grid>
        ))}
      </Grid>

      {result?.error && <Alert severity="error">{result.error}</Alert>}

      {result && !result.error && (
        <Paper variant="outlined">
          <Box sx={{ p: 2 }}>
            <Alert severity={result.ok ? "success" : "error"}>
              {result.ok
                ? selected?.attack
                  ? `${selected.label}：想定どおり拒否された`
                  : `${selected?.label}：最後まで通った`
                : "想定と違う応答がある（下の表の ✕ の行）"}
            </Alert>
          </Box>
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>#</TableCell>
                  <TableCell>呼び出し</TableCell>
                  <TableCell>種類</TableCell>
                  <TableCell>Cookie</TableCell>
                  <TableCell>応答</TableCell>
                  <TableCell>何が起きたか</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {result.steps.map((step, index) => (
                  <TableRow key={index} sx={{ verticalAlign: "top" }}>
                    <TableCell>{index + 1}</TableCell>
                    <TableCell sx={{ minWidth: 220 }}>
                      <Typography variant="body2" fontWeight="bold">
                        {step.title}
                      </Typography>
                      <Typography
                        variant="caption"
                        sx={{ fontFamily: "monospace", wordBreak: "break-all", color: "text.secondary" }}
                      >
                        {step.request.length > 90 ? `${step.request.slice(0, 90)}…` : step.request}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Chip size="small" variant="outlined" label={KIND_LABEL[step.kind]} />
                    </TableCell>
                    <TableCell>
                      {step.cookie !== "-" && (
                        <Chip
                          size="small"
                          color={step.cookie === "sent" ? "success" : "error"}
                          label={COOKIE_LABEL[step.cookie]}
                        />
                      )}
                    </TableCell>
                    <TableCell>
                      <Stack direction="row" spacing={0.5} alignItems="center">
                        {step.ok ? (
                          <CheckCircleIcon color="success" fontSize="small" />
                        ) : (
                          <CancelIcon color="error" fontSize="small" />
                        )}
                        <Typography variant="body2" sx={{ fontFamily: "monospace" }}>
                          {step.status}
                        </Typography>
                      </Stack>
                      {!step.ok && (
                        <Typography variant="caption" color="error">
                          期待 {step.expected}
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell sx={{ minWidth: 260 }}>
                      <Typography variant="body2">{step.note}</Typography>
                      {step.details && Object.keys(step.details).length > 0 && (
                        <Box
                          component="pre"
                          sx={{
                            m: 0,
                            mt: 0.5,
                            fontSize: 11,
                            whiteSpace: "pre-wrap",
                            wordBreak: "break-all",
                            color: "text.secondary",
                          }}
                        >
                          {JSON.stringify(step.details, null, 2)}
                        </Box>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          {result.idToken && (
            <Box sx={{ p: 2 }}>
              <Typography variant="body2" fontWeight="bold">
                発行された ID Token
              </Typography>
              <Typography variant="body2" sx={{ fontFamily: "monospace" }}>
                sid: {result.idToken.sid ?? "なし"} ／ amr: {(result.idToken.amr ?? []).join(", ")} ／
                auth_time: {formatTime(result.idToken.auth_time)}
              </Typography>
            </Box>
          )}
        </Paper>
      )}
    </Stack>
  );
};

/* ------------------------------------------------------------------ */

export default function CrossSiteDemoPage() {
  return (
    <Container maxWidth="lg" sx={{ py: 4 }}>
      <Stack spacing={5}>
        <Box>
          <Typography variant="h4" component="h1" fontWeight="bold">
            認可画面の構成デモ
          </Typography>
          <Typography color="text.secondary" sx={{ mt: 1, maxWidth: 760 }}>
            認可画面を idp-server と同じサイトに置く構成と、別のサイトに置く構成で、同じログインがどう成立しているかを並べて見るページです。
            別サイト構成では、認可画面から idp-server への呼び出しがすべてサードパーティになり、Safari は Cookie を送りも保存もしません。
            そこでブラウザの識別を使い捨ての値（auth_proof）に移し、最後に idp-server を一度トップレベル遷移で経由させています。
          </Typography>
        </Box>

        <Box>
          <Typography variant="h5" fontWeight="bold" sx={{ mb: 2 }}>
            1. 2 つの構成のシーケンス
          </Typography>
          <Grid container spacing={2}>
            <Grid item xs={12} md={6}>
              <SequenceCard
                title="同一サイトの認可画面"
                host="auth.local.test → api.local.test"
                steps={SAME_SITE}
                accent="primary"
              />
            </Grid>
            <Grid item xs={12} md={6}>
              <SequenceCard
                title="別サイトの認可画面"
                host="auth.idp.local → api.local.test"
                steps={CROSS_SITE}
                accent="secondary"
              />
            </Grid>
          </Grid>
          <Alert severity="info" sx={{ mt: 2 }}>
            どちらの構成で動くかはクライアントの設定（extension.cross_site_authorization_view）で決まります。未指定ならテナントの
            ui_config.cross_site に従います。このデモでは 2 つのクライアントを使い分けています。
          </Alert>
        </Box>

        <Divider />

        <Box>
          <Typography variant="h5" fontWeight="bold" sx={{ mb: 2 }}>
            2. ブラウザでログインしてみる
          </Typography>
          <LiveSignIn />
        </Box>

        <Divider />

        <Box>
          <Typography variant="h5" fontWeight="bold" sx={{ mb: 2 }}>
            3. 境界をステップで再生する
          </Typography>
          <Replay />
        </Box>
      </Stack>
    </Container>
  );
}
