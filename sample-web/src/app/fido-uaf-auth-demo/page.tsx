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
  Chip,
  Paper,
  TextField,
  CircularProgress,
} from "@mui/material";
import { useState, useEffect } from "react";
import * as jose from "jose";
import FingerprintIcon from "@mui/icons-material/Fingerprint";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import ErrorIcon from "@mui/icons-material/Error";
import PlayArrowIcon from "@mui/icons-material/PlayArrow";
import RestartAltIcon from "@mui/icons-material/RestartAlt";
import HomeIcon from "@mui/icons-material/Home";
import NotificationsActiveIcon from "@mui/icons-material/NotificationsActive";
import PollIcon from "@mui/icons-material/Poll";

interface TestResult {
  step: string;
  success: boolean;
  message: string;
  details?: Record<string, unknown>;
}

type DemoStep =
  | "start"
  | "setup-user-registered"
  | "setup-fido-challenged"
  | "setup-device-registered"
  | "setup-authorized"
  | "demo-auth-started"
  | "demo-view-data"
  | "demo-status-before"
  | "demo-push-notification"
  | "demo-fido-uaf-challenge"
  | "demo-fido-uaf-auth"
  | "demo-status-after"
  | "demo-authorized"
  | "completed";

export default function FidoUafAuthDemoPage() {
  const [email, setEmail] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");

  const [currentStep, setCurrentStep] = useState<DemoStep>("start");
  const [isRunning, setIsRunning] = useState(false);
  const [testResults, setTestResults] = useState<TestResult[]>([]);

  // Setup phase state
  const [setupAuthId, setSetupAuthId] = useState<string | null>(null);
  const [setupCodeVerifier, setSetupCodeVerifier] = useState<string | null>(null);
  const [registeredUserId, setRegisteredUserId] = useState<string | null>(null);
  const [deviceId, setDeviceId] = useState<string | null>(null);
  const [deviceSecret, setDeviceSecret] = useState<string | null>(null);
  const [deviceSecretAlgorithm, setDeviceSecretAlgorithm] = useState<string>("HS256");
  const [deviceSecretJwtIssuer, setDeviceSecretJwtIssuer] = useState<string>("");

  // Demo phase state
  const [demoAuthId, setDemoAuthId] = useState<string | null>(null);
  const [demoCodeVerifier, setDemoCodeVerifier] = useState<string | null>(null);
  const [idTokenPayload, setIdTokenPayload] = useState<Record<string, unknown> | null>(null);

  useEffect(() => {
    if (typeof window !== "undefined") {
      const ts = Date.now();
      setEmail(`demo-${ts}@example.com`);
      setUsername(`Demo User ${ts}`);
      setPassword(`DemoPass${ts}!`);
    }
  }, []);

  const addResult = (result: TestResult) => {
    setTestResults((prev) => [...prev, result]);
  };

  const resetTest = () => {
    setSetupAuthId(null);
    setSetupCodeVerifier(null);
    setRegisteredUserId(null);
    setDeviceId(null);
    setDeviceSecret(null);
    setDeviceSecretAlgorithm("HS256");
    setDeviceSecretJwtIssuer("");
    setDemoAuthId(null);
    setDemoCodeVerifier(null);
    setIdTokenPayload(null);
    setTestResults([]);
    setCurrentStep("start");
    setIsRunning(false);
    const ts = Date.now();
    setEmail(`demo-${ts}@example.com`);
    setUsername(`Demo User ${ts}`);
    setPassword(`DemoPass${ts}!`);
  };

  // ===== Phase 1: Setup =====

  const setupStartAuth = async () => {
    setIsRunning(true);
    setTestResults([]);

    const res = await fetch("/api/fido-uaf-auth-test?action=start", { credentials: "include" });
    const data = await res.json();

    if (data.success) {
      setSetupAuthId(data.authorizationId);
      setSetupCodeVerifier(data.codeVerifier);
      addResult({ step: "[Setup] Authorization Start", success: true, message: `ID: ${data.authorizationId}` });
    } else {
      addResult({ step: "[Setup] Authorization Start", success: false, message: "Failed", details: data });
      setIsRunning(false);
    }
  };

  const setupRegisterUser = async () => {
    if (!setupAuthId) return;
    const res = await fetch(`/api/fido-uaf-auth-test?action=initial-registration&authorizationId=${setupAuthId}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify({ email, password, name: username }),
    });
    const data = await res.json();
    if (data.success) {
      const userId = data.data.user?.sub || "";
      setRegisteredUserId(userId);
      addResult({ step: "[Setup] User Registration", success: true, message: `User: ${userId}` });
      setCurrentStep("setup-user-registered");
    } else {
      addResult({ step: "[Setup] User Registration", success: false, message: "Failed", details: data });
      setIsRunning(false);
    }
  };

  const setupFidoChallenge = async () => {
    if (!setupAuthId) return;
    const res = await fetch(`/api/fido-uaf-auth-test?action=fido-uaf-registration-challenge&authorizationId=${setupAuthId}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify({ app_name: "FIDO-UAF Auth Demo", platform: "Android", os: "Android15", model: "demo-device", notification_channel: "fcm", notification_token: "demo-fcm-token", preferred_for_notification: true }),
    });
    const data = await res.json();
    if (data.success) {
      addResult({ step: "[Setup] FIDO-UAF Challenge", success: true, message: "Challenge received" });
      setCurrentStep("setup-fido-challenged");
    } else {
      addResult({ step: "[Setup] FIDO-UAF Challenge", success: false, message: "Failed", details: data });
      setIsRunning(false);
    }
  };

  const setupFidoRegister = async () => {
    if (!setupAuthId) return;
    const res = await fetch(`/api/fido-uaf-auth-test?action=fido-uaf-registration&authorizationId=${setupAuthId}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify({ uafResponse: [{ assertionScheme: "UAFV1TLV", assertion: "mock_assertion_data" }] }),
    });
    const data = await res.json();
    if (data.success && data.data.device_id) {
      setDeviceId(data.data.device_id);
      if (data.data.device_secret) {
        setDeviceSecret(data.data.device_secret);
        setDeviceSecretAlgorithm(data.data.device_secret_algorithm || "HS256");
        setDeviceSecretJwtIssuer(data.data.device_secret_jwt_issuer || "");
      }
      addResult({ step: "[Setup] Device Registered", success: true, message: `Device: ${data.data.device_id}${data.data.device_secret ? " (with device_secret)" : ""}` });
      setCurrentStep("setup-device-registered");
    } else {
      addResult({ step: "[Setup] Device Registered", success: false, message: "Failed", details: data });
      setIsRunning(false);
    }
  };

  const setupAuthorize = async () => {
    if (!setupAuthId) return;
    const authRes = await fetch(`/api/fido-uaf-auth-test?action=authorize&authorizationId=${setupAuthId}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
    });
    const authData = await authRes.json();
    if (authData.success && authData.data.redirect_uri) {
      const redirectUrl = new URL(authData.data.redirect_uri);
      const code = redirectUrl.searchParams.get("code");
      if (code) {
        const tokenRes = await fetch("/api/fido-uaf-auth-test?action=token-exchange", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ code, codeVerifier: setupCodeVerifier }),
        });
        const tokenData = await tokenRes.json();
        if (tokenData.success) {
          const accessToken = tokenData.data.access_token;

          // Update device with notification_channel
          if (deviceId && accessToken) {
            const updateRes = await fetch("/api/fido-uaf-auth-test?action=update-device", {
              method: "POST",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify({
                accessToken,
                deviceIdToUpdate: deviceId,
                notificationChannel: "fcm",
                notificationToken: "demo-fcm-token",
              }),
            });
            const updateData = await updateRes.json();
            if (updateData.success) {
              addResult({ step: "[Setup] Device Updated", success: true, message: "notification_channel=fcm set" });
            } else {
              addResult({ step: "[Setup] Device Updated", success: false, message: "Failed to update device", details: updateData });
            }
          }

          addResult({ step: "[Setup] Complete", success: true, message: "User + Device ready" });
          setCurrentStep("setup-authorized");
          return;
        }
      }
    }
    addResult({ step: "[Setup] Complete", success: false, message: "Failed to complete setup", details: authData });
    setIsRunning(false);
  };

  // ===== Phase 2: Demo =====

  const demoStartAuth = async () => {
    if (!registeredUserId) return;
    const loginHint = `sub:${registeredUserId},idp:idp-server`;
    const res = await fetch(`/api/fido-uaf-auth-test?action=start-with-login-hint&loginHint=${encodeURIComponent(loginHint)}`, {
      credentials: "include",
    });
    const data = await res.json();
    if (data.success) {
      setDemoAuthId(data.authorizationId);
      setDemoCodeVerifier(data.codeVerifier);
      addResult({ step: "Authorization with login_hint", success: true, message: `login_hint=${loginHint}`, details: { authorizationId: data.authorizationId } });
      setCurrentStep("demo-auth-started");
    } else {
      addResult({ step: "Authorization with login_hint", success: false, message: "Failed", details: data });
      setIsRunning(false);
    }
  };

  const demoViewData = async () => {
    if (!demoAuthId) return;
    const res = await fetch(`/api/fido-uaf-auth-test?action=view-data&authorizationId=${demoAuthId}`, { credentials: "include" });
    const data = await res.json();
    if (data.success) {
      const hasLoginHint = !!data.data.login_hint;
      addResult({
        step: "View Data (login_hint)",
        success: hasLoginHint,
        message: hasLoginHint ? `login_hint: ${data.data.login_hint}` : "login_hint not found in view-data",
        details: { login_hint: data.data.login_hint, session_enabled: data.data.session_enabled },
      });
      setCurrentStep("demo-view-data");
    } else {
      addResult({ step: "View Data", success: false, message: "Failed", details: data });
      setIsRunning(false);
    }
  };

  const demoStatusBefore = async () => {
    if (!demoAuthId) return;
    const res = await fetch(`/api/fido-uaf-auth-test?action=authentication-status&authorizationId=${demoAuthId}`, { credentials: "include" });
    const data = await res.json();
    if (data.success) {
      addResult({
        step: "Authentication Status (before)",
        success: data.data.status === "in_progress",
        message: `status: ${data.data.status}`,
        details: data.data,
      });
      setCurrentStep("demo-status-before");
    } else {
      addResult({ step: "Authentication Status (before)", success: false, message: "Failed", details: data });
      setIsRunning(false);
    }
  };

  const demoPushNotification = async () => {
    if (!demoAuthId) return;
    const res = await fetch(`/api/fido-uaf-auth-test?action=authentication-device-notification&authorizationId=${demoAuthId}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
    });
    const data = await res.json();
    addResult({
      step: "Push Notification",
      success: true,
      message: data.success ? "Push notification sent" : `Push notification failed (expected without FCM): ${data.data?.error_description || ""}`,
      details: data,
    });
    setCurrentStep("demo-push-notification");
  };

  const createDeviceSecretJwt = async (): Promise<string | null> => {
    if (!deviceSecret || !deviceId) return null;
    const secret = new TextEncoder().encode(deviceSecret);
    const jwt = await new jose.SignJWT({})
      .setProtectedHeader({ alg: deviceSecretAlgorithm, typ: "JWT" })
      .setIssuer(deviceSecretJwtIssuer)
      .setSubject(deviceId)
      .setAudience(process.env.NEXT_PUBLIC_IDP_SERVER_ISSUER || window.location.origin)
      .setJti(crypto.randomUUID())
      .setIssuedAt()
      .setExpirationTime("5m")
      .sign(secret);
    return jwt;
  };

  const demoFidoUafAuth = async () => {
    if (!demoAuthId || !deviceId) return;

    // Create device_secret_jwt for device authentication
    const jwt = await createDeviceSecretJwt();

    // Step 1: Device fetches authentication transactions (simulating device receiving push)
    const txRes = await fetch("/api/fido-uaf-auth-test?action=device-authentications", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ deviceId, authorizationId: demoAuthId, deviceSecretJwt: jwt }),
    });
    const txData = await txRes.json();
    if (!txData.success || !txData.data.list?.length) {
      addResult({ step: "Device: Get Auth Transactions", success: false, message: "No transactions found for device", details: txData });
      setIsRunning(false);
      return;
    }
    const transaction = txData.data.list[0];
    const transactionId = transaction.id;
    addResult({ step: "Device: Get Auth Transactions", success: true, message: `Transaction: ${transactionId} (flow: ${transaction.flow})` });
    setCurrentStep("demo-fido-uaf-challenge");

    // Step 2: FIDO-UAF authentication challenge
    const challengeRes = await fetch("/api/fido-uaf-auth-test?action=fido-uaf-auth-challenge", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ transactionId }),
    });
    const challengeData = await challengeRes.json();
    if (!challengeData.success) {
      addResult({ step: "Device: FIDO-UAF Challenge", success: false, message: "Challenge failed", details: challengeData });
      setIsRunning(false);
      return;
    }
    addResult({ step: "Device: FIDO-UAF Challenge", success: true, message: "Challenge received from FIDO server" });

    // Step 3: FIDO-UAF authentication (biometric simulation)
    const authRes = await fetch("/api/fido-uaf-auth-test?action=fido-uaf-auth", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ transactionId }),
    });
    const authData = await authRes.json();
    if (authData.success) {
      addResult({ step: "Device: FIDO-UAF Authentication", success: true, message: "Device authenticated via FIDO-UAF (biometric)" });
      setCurrentStep("demo-fido-uaf-auth");
    } else {
      addResult({ step: "Device: FIDO-UAF Authentication", success: false, message: "FIDO-UAF auth failed", details: authData });
      setIsRunning(false);
    }
  };

  const demoStatusAfter = async () => {
    if (!demoAuthId) return;
    const res = await fetch(`/api/fido-uaf-auth-test?action=authentication-status&authorizationId=${demoAuthId}`, { credentials: "include" });
    const data = await res.json();
    if (data.success) {
      addResult({
        step: "Authentication Status (after)",
        success: data.data.status === "success",
        message: `status: ${data.data.status}, methods: [${data.data.authentication_methods?.join(", ")}]`,
        details: data.data,
      });
      setCurrentStep("demo-status-after");
    } else {
      addResult({ step: "Authentication Status (after)", success: false, message: "Failed", details: data });
      setIsRunning(false);
    }
  };

  const demoAuthorize = async () => {
    if (!demoAuthId) return;
    const authRes = await fetch(`/api/fido-uaf-auth-test?action=authorize&authorizationId=${demoAuthId}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
    });
    const authData = await authRes.json();
    if (authData.success && authData.data.redirect_uri) {
      const redirectUrl = new URL(authData.data.redirect_uri);
      const code = redirectUrl.searchParams.get("code");
      if (code) {
        const tokenRes = await fetch("/api/fido-uaf-auth-test?action=token-exchange", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ code, codeVerifier: demoCodeVerifier }),
        });
        const tokenData = await tokenRes.json();
        if (tokenData.success && tokenData.data.id_token) {
          const decoded = jose.decodeJwt(tokenData.data.id_token);
          setIdTokenPayload(decoded as Record<string, unknown>);
          addResult({
            step: "Token Issued",
            success: true,
            message: `sub: ${decoded.sub}, amr: [${(decoded.amr as string[])?.join(", ") || "N/A"}]`,
            details: { sub: decoded.sub, amr: decoded.amr, auth_time: decoded.auth_time },
          });
          setCurrentStep("completed");
          setIsRunning(false);
          return;
        }
      }
    }
    addResult({ step: "Token Issued", success: false, message: "Failed", details: authData });
    setIsRunning(false);
  };

  // Auto-continue
  useEffect(() => {
    if (!isRunning) return;
    if (currentStep === "start" && !setupAuthId) return;

    const continueDemo = async () => {
      switch (currentStep) {
        case "start": await setupRegisterUser(); break;
        case "setup-user-registered": await setupFidoChallenge(); break;
        case "setup-fido-challenged": await setupFidoRegister(); break;
        case "setup-device-registered": await setupAuthorize(); break;
        case "setup-authorized": await demoStartAuth(); break;
        case "demo-auth-started": await demoViewData(); break;
        case "demo-view-data": await demoStatusBefore(); break;
        case "demo-status-before": await demoPushNotification(); break;
        case "demo-push-notification": await demoFidoUafAuth(); break;
        case "demo-fido-uaf-auth": await demoStatusAfter(); break;
        case "demo-status-after": await demoAuthorize(); break;
      }
    };

    const timer = setTimeout(continueDemo, 500);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentStep, isRunning, setupAuthId]);

  const steps = [
    "[Setup] User Registration",
    "[Setup] FIDO-UAF Device",
    "[Setup] Authorize",
    "login_hint Auth",
    "View Data",
    "Status (before)",
    "Push Notification",
    "FIDO-UAF Auth",
    "Status (after)",
    "Token Issued",
  ];

  const getActiveStep = () => {
    const stepMap: Record<DemoStep, number> = {
      start: 0,
      "setup-user-registered": 0,
      "setup-fido-challenged": 1,
      "setup-device-registered": 1,
      "setup-authorized": 2,
      "demo-auth-started": 3,
      "demo-view-data": 4,
      "demo-status-before": 5,
      "demo-push-notification": 6,
      "demo-fido-uaf-challenge": 7,
      "demo-fido-uaf-auth": 7,
      "demo-status-after": 8,
      "demo-authorized": 9,
      completed: 10,
    };
    return stepMap[currentStep];
  };

  return (
    <Container maxWidth="lg" sx={{ py: 4 }}>
      <Stack spacing={3}>
        <Card>
          <CardContent>
            <Stack direction="row" alignItems="center" spacing={2} sx={{ mb: 2 }}>
              <FingerprintIcon sx={{ fontSize: 48, color: "primary.main" }} />
              <Box>
                <Typography variant="h4" component="h1">
                  FIDO-UAF Authorization Code Flow Demo
                </Typography>
                <Typography variant="body1" color="text.secondary">
                  login_hint + authentication-status + Push Notification
                </Typography>
              </Box>
            </Stack>

            <Alert severity="info" sx={{ mt: 2 }}>
              <AlertTitle>Demo Overview</AlertTitle>
              <Typography variant="body2" component="div">
                <strong>Phase 1 (Setup):</strong> Register user + FIDO-UAF device
                <br />
                <strong>Phase 2 (Demo):</strong> Authorization Code Flow with login_hint
                <ol style={{ margin: "4px 0", paddingLeft: "20px" }}>
                  <li><strong>login_hint</strong> - Authorization request with user pre-resolution</li>
                  <li><strong>view-data</strong> - Verify login_hint returned to SPA</li>
                  <li><strong>authentication-status</strong> - Polling: in_progress → success</li>
                  <li><strong>Push notification</strong> - Send device notification (FCM)</li>
                  <li><strong>FIDO-UAF auth</strong> - Device authentication via /authentications/</li>
                  <li><strong>Token issuance</strong> - Authorization code → tokens</li>
                </ol>
              </Typography>
            </Alert>
          </CardContent>
        </Card>

        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>Demo User</Typography>
            <Stack direction="row" spacing={2} sx={{ mb: 2 }}>
              <TextField label="Email" value={email} onChange={(e) => setEmail(e.target.value)} size="small" fullWidth disabled={isRunning} />
              <TextField label="Name" value={username} onChange={(e) => setUsername(e.target.value)} size="small" fullWidth disabled={isRunning} />
              <TextField label="Password" value={password} onChange={(e) => setPassword(e.target.value)} size="small" fullWidth disabled={isRunning} type="password" />
            </Stack>
            <Stack direction="row" spacing={2}>
              <Button variant="contained" color="primary" startIcon={isRunning ? <CircularProgress size={20} /> : <PlayArrowIcon />} onClick={setupStartAuth} disabled={isRunning}>
                {isRunning ? "Running..." : "Run Demo"}
              </Button>
              <Button variant="outlined" startIcon={<RestartAltIcon />} onClick={resetTest} disabled={isRunning}>Reset</Button>
              <Button variant="outlined" startIcon={<HomeIcon />} href="/">Home</Button>
            </Stack>
          </CardContent>
        </Card>

        {testResults.length > 0 && (
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>Progress</Typography>
              <Stepper activeStep={getActiveStep()} alternativeLabel sx={{ overflowX: "auto" }}>
                {steps.map((label) => (
                  <Step key={label}><StepLabel sx={{ "& .MuiStepLabel-label": { fontSize: "0.7rem" } }}>{label}</StepLabel></Step>
                ))}
              </Stepper>
            </CardContent>
          </Card>
        )}

        {/* Key Highlights */}
        {demoAuthId && (
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                <NotificationsActiveIcon sx={{ mr: 1, verticalAlign: "middle", color: "warning.main" }} />
                New Features Demonstrated
              </Typography>
              <Stack spacing={1}>
                {testResults.filter(r => ["View Data (login_hint)", "Authentication Status (before)", "Push Notification", "Device: FIDO-UAF Authentication", "Authentication Status (after)"].includes(r.step)).map((r, i) => (
                  <Alert key={i} severity={r.success ? "success" : "warning"} variant="outlined" sx={{ py: 0 }}>
                    <Stack direction="row" alignItems="center" spacing={1}>
                      <PollIcon fontSize="small" />
                      <Typography variant="body2"><strong>{r.step}:</strong> {r.message}</Typography>
                    </Stack>
                  </Alert>
                ))}
              </Stack>
            </CardContent>
          </Card>
        )}

        {/* ID Token */}
        {idTokenPayload && (
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>ID Token</Typography>
              <Stack direction="row" spacing={1} sx={{ mb: 1 }}>
                <Chip label={`sub: ${idTokenPayload.sub}`} color="primary" size="small" />
                {(idTokenPayload.amr as string[])?.map((m) => (
                  <Chip key={m} label={`amr: ${m}`} color="success" size="small" />
                ))}
              </Stack>
              <Paper variant="outlined" sx={{ p: 1, maxHeight: 200, overflow: "auto" }}>
                <pre style={{ margin: 0, fontSize: "11px" }}>{JSON.stringify(idTokenPayload, null, 2)}</pre>
              </Paper>
            </CardContent>
          </Card>
        )}

        {/* Execution Log */}
        {testResults.length > 0 && (
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>Execution Log</Typography>
              <Stepper orientation="vertical" activeStep={testResults.length}>
                {testResults.map((result, index) => (
                  <Step key={index} completed>
                    <StepLabel icon={result.success ? <CheckCircleIcon color="success" /> : <ErrorIcon color="error" />}>
                      <Typography variant="subtitle2">{result.step}</Typography>
                    </StepLabel>
                    <Box sx={{ ml: 4, mb: 2 }}>
                      <Alert severity={result.success ? "success" : "error"} variant="outlined" sx={{ py: 0 }}>
                        {result.message}
                      </Alert>
                      {result.details && (
                        <Paper variant="outlined" sx={{ p: 1, mt: 1, backgroundColor: "grey.50" }}>
                          <pre style={{ margin: 0, fontSize: "11px", overflow: "auto" }}>{JSON.stringify(result.details, null, 2)}</pre>
                        </Paper>
                      )}
                    </Box>
                  </Step>
                ))}
              </Stepper>
            </CardContent>
          </Card>
        )}
      </Stack>
    </Container>
  );
}
