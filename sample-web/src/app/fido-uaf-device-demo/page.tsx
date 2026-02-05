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
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  CircularProgress,
  ToggleButton,
  ToggleButtonGroup,
} from "@mui/material";
import { useState, useEffect } from "react";
import * as jose from "jose";
import PhoneAndroidIcon from "@mui/icons-material/PhoneAndroid";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import ErrorIcon from "@mui/icons-material/Error";
import SecurityIcon from "@mui/icons-material/Security";
import PlayArrowIcon from "@mui/icons-material/PlayArrow";
import RestartAltIcon from "@mui/icons-material/RestartAlt";
import KeyIcon from "@mui/icons-material/Key";
import VpnKeyIcon from "@mui/icons-material/VpnKey";
import TokenIcon from "@mui/icons-material/Token";
import DevicesIcon from "@mui/icons-material/Devices";
import LockIcon from "@mui/icons-material/Lock";
import LockOpenIcon from "@mui/icons-material/LockOpen";
import HomeIcon from "@mui/icons-material/Home";

interface TestResult {
  step: string;
  success: boolean;
  message: string;
  details?: Record<string, unknown>;
}

interface DeviceCredential {
  deviceId: string;
  deviceSecret: string;
  deviceSecretAlgorithm: string;
  deviceSecretJwtIssuer: string;
  userId: string;
}

// Demo flow steps
type DemoStep =
  | "start"
  | "user-registered"
  | "fido-uaf-challenged"
  | "device-registered"
  | "authorized"
  | "ciba-requested"
  | "device-auth-without-jwt"
  | "device-auth-with-jwt"
  | "jwt-bearer-grant"
  | "completed";

// Algorithm type
type AlgorithmType = "HS256" | "HS384" | "HS512";

const ALGORITHM_INFO: Record<AlgorithmType, { bits: number; bytes: number }> = {
  HS256: { bits: 256, bytes: 32 },
  HS384: { bits: 384, bytes: 48 },
  HS512: { bits: 512, bytes: 64 },
};

export default function FidoUafDeviceDemoPage() {
  const [email, setEmail] = useState<string>("");
  const [username, setUsername] = useState<string>("");
  const [password, setPassword] = useState<string>("");

  // Demo flow state
  const [currentStep, setCurrentStep] = useState<DemoStep>("start");
  const [isRunning, setIsRunning] = useState(false);
  const [authorizationId, setAuthorizationId] = useState<string | null>(null);
  const [codeVerifier, setCodeVerifier] = useState<string | null>(null);
  const [testResults, setTestResults] = useState<TestResult[]>([]);

  // Device credential state
  const [deviceCredential, setDeviceCredential] = useState<DeviceCredential | null>(null);
  const [authReqId, setAuthReqId] = useState<string | null>(null);
  const [registeredUserId, setRegisteredUserId] = useState<string | null>(null);

  // Algorithm selection
  const [algorithm, setAlgorithm] = useState<AlgorithmType>("HS256");

  // Results
  const [deviceAuthWithoutJwt, setDeviceAuthWithoutJwt] = useState<{
    status: number;
    hasContext: boolean;
  } | null>(null);
  const [deviceAuthWithJwt, setDeviceAuthWithJwt] = useState<{
    status: number;
    hasContext: boolean;
  } | null>(null);
  const [jwtBearerGrantResult, setJwtBearerGrantResult] = useState<{
    success: boolean;
    accessToken?: string;
  } | null>(null);

  useEffect(() => {
    if (typeof window !== "undefined") {
      const timestamp = Date.now();
      setEmail(`demo-${timestamp}@example.com`);
      setUsername(`Demo User ${timestamp}`);
      setPassword(`DemoPass${timestamp}!`);
    }
  }, []);

  const addResult = (result: TestResult) => {
    setTestResults((prev) => [...prev, result]);
  };

  const resetTest = () => {
    setAuthorizationId(null);
    setCodeVerifier(null);
    setTestResults([]);
    setCurrentStep("start");
    setDeviceCredential(null);
    setAuthReqId(null);
    setRegisteredUserId(null);
    setDeviceAuthWithoutJwt(null);
    setDeviceAuthWithJwt(null);
    setJwtBearerGrantResult(null);
    const timestamp = Date.now();
    setEmail(`demo-${timestamp}@example.com`);
    setUsername(`Demo User ${timestamp}`);
    setPassword(`DemoPass${timestamp}!`);
  };

  // Step 1: Start authorization
  const startAuthorization = async () => {
    setIsRunning(true);
    setTestResults([]);

    try {
      const response = await fetch("/api/fido-uaf-device-test?action=start", {
        credentials: "include",
      });
      const data = await response.json();

      if (data.success && data.authorizationId) {
        setAuthorizationId(data.authorizationId);
        setCodeVerifier(data.codeVerifier);
        addResult({
          step: "Start Authorization",
          success: true,
          message: `Authorization started: ${data.authorizationId}`,
          details: { tenantId: data.tenantId },
        });
      } else {
        addResult({
          step: "Start Authorization",
          success: false,
          message: "Failed to start authorization",
          details: data,
        });
        setIsRunning(false);
        return;
      }
    } catch (error) {
      addResult({
        step: "Start Authorization",
        success: false,
        message: `Error: ${error instanceof Error ? error.message : String(error)}`,
      });
      setIsRunning(false);
      return;
    }
  };

  // Step 2: Register user
  const registerUser = async () => {
    if (!authorizationId) return;

    try {
      const response = await fetch(
        `/api/fido-uaf-device-test?action=initial-registration&authorizationId=${authorizationId}`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          credentials: "include",
          body: JSON.stringify({
            email,
            password,
            name: username,
          }),
        }
      );
      const data = await response.json();

      if (data.success) {
        const userId = data.data.user?.sub || "";
        setRegisteredUserId(userId);
        addResult({
          step: "User Registration",
          success: true,
          message: `User registered: ${userId || "unknown"}`,
          details: { userId },
        });
        setCurrentStep("user-registered");
      } else {
        addResult({
          step: "User Registration",
          success: false,
          message: "Failed to register user",
          details: data,
        });
        setIsRunning(false);
      }
    } catch (error) {
      addResult({
        step: "User Registration",
        success: false,
        message: `Error: ${error instanceof Error ? error.message : String(error)}`,
      });
      setIsRunning(false);
    }
  };

  // Step 3: FIDO-UAF registration challenge
  const getFidoUafChallenge = async () => {
    if (!authorizationId) return;

    try {
      const response = await fetch(
        `/api/fido-uaf-device-test?action=fido-uaf-registration-challenge&authorizationId=${authorizationId}`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          credentials: "include",
          body: JSON.stringify({
            app_name: "Device Credential Demo",
            platform: "Web",
            os: navigator.platform,
            model: navigator.userAgent.substring(0, 50),
          }),
        }
      );
      const data = await response.json();

      if (data.success) {
        addResult({
          step: "FIDO-UAF Challenge",
          success: true,
          message: "Challenge received from FIDO-UAF server",
          details: { hasChallenge: !!data.data.challenge },
        });
        setCurrentStep("fido-uaf-challenged");
      } else {
        addResult({
          step: "FIDO-UAF Challenge",
          success: false,
          message: "Failed to get FIDO-UAF challenge",
          details: data,
        });
        setIsRunning(false);
      }
    } catch (error) {
      addResult({
        step: "FIDO-UAF Challenge",
        success: false,
        message: `Error: ${error instanceof Error ? error.message : String(error)}`,
      });
      setIsRunning(false);
    }
  };

  // Step 4: Complete FIDO-UAF registration (mock)
  const completeFidoUafRegistration = async () => {
    if (!authorizationId) return;

    try {
      const response = await fetch(
        `/api/fido-uaf-device-test?action=fido-uaf-registration&authorizationId=${authorizationId}`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          credentials: "include",
          body: JSON.stringify({
            uafResponse: [{ assertionScheme: "UAFV1TLV", assertion: "mock_assertion_data" }],
          }),
        }
      );
      const data = await response.json();

      if (data.success && data.data.device_secret) {
        const credential: DeviceCredential = {
          deviceId: data.data.device_id,
          deviceSecret: data.data.device_secret,
          deviceSecretAlgorithm: data.data.device_secret_algorithm,
          deviceSecretJwtIssuer: data.data.device_secret_jwt_issuer,
          userId: registeredUserId || data.data.user?.sub || "",
        };
        setDeviceCredential(credential);

        addResult({
          step: "FIDO-UAF Registration",
          success: true,
          message: `Device registered with device_secret (${credential.deviceSecretAlgorithm})`,
          details: {
            deviceId: credential.deviceId,
            userId: credential.userId,
            algorithm: credential.deviceSecretAlgorithm,
            secretLength: credential.deviceSecret.length,
            jwtIssuer: credential.deviceSecretJwtIssuer,
          },
        });
        setCurrentStep("device-registered");
      } else {
        addResult({
          step: "FIDO-UAF Registration",
          success: false,
          message: data.data.device_secret
            ? "Failed to complete FIDO-UAF registration"
            : "device_secret not issued (check tenant policy: issue_device_secret)",
          details: data,
        });
        setIsRunning(false);
      }
    } catch (error) {
      addResult({
        step: "FIDO-UAF Registration",
        success: false,
        message: `Error: ${error instanceof Error ? error.message : String(error)}`,
      });
      setIsRunning(false);
    }
  };

  // Step 5: Complete authorization
  const completeAuthorization = async () => {
    if (!authorizationId) return;

    try {
      const response = await fetch(
        `/api/fido-uaf-device-test?action=authorize&authorizationId=${authorizationId}`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          credentials: "include",
        }
      );
      const data = await response.json();

      if (data.success && data.data.redirect_uri) {
        const redirectUrl = new URL(data.data.redirect_uri);
        const code = redirectUrl.searchParams.get("code");

        if (code) {
          // Exchange code for tokens
          const tokenResponse = await fetch(`/api/fido-uaf-device-test?action=token-exchange`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              code,
              codeVerifier,
            }),
          });
          const tokenData = await tokenResponse.json();

          if (tokenData.success) {
            addResult({
              step: "Authorization Complete",
              success: true,
              message: "Authorization code exchanged for tokens",
              details: { hasAccessToken: !!tokenData.data.access_token },
            });
            setCurrentStep("authorized");
          } else {
            addResult({
              step: "Authorization Complete",
              success: false,
              message: "Failed to exchange authorization code",
              details: tokenData,
            });
          }
        }
      } else {
        addResult({
          step: "Authorization Complete",
          success: false,
          message: "Failed to complete authorization",
          details: data,
        });
        setIsRunning(false);
      }
    } catch (error) {
      addResult({
        step: "Authorization Complete",
        success: false,
        message: `Error: ${error instanceof Error ? error.message : String(error)}`,
      });
      setIsRunning(false);
    }
  };

  // Step 6: Start CIBA request
  const startCibaRequest = async () => {
    if (!deviceCredential) return;

    try {
      const response = await fetch(`/api/fido-uaf-device-test?action=ciba-request`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          deviceId: deviceCredential.deviceId,
          userCode: password,
        }),
      });
      const data = await response.json();

      if (data.success && data.data.auth_req_id) {
        setAuthReqId(data.data.auth_req_id);
        addResult({
          step: "CIBA Request",
          success: true,
          message: `CIBA request initiated: ${data.data.auth_req_id}`,
          details: { authReqId: data.data.auth_req_id, expiresIn: data.data.expires_in },
        });
        setCurrentStep("ciba-requested");
      } else {
        addResult({
          step: "CIBA Request",
          success: false,
          message: "Failed to start CIBA request",
          details: data,
        });
        setIsRunning(false);
      }
    } catch (error) {
      addResult({
        step: "CIBA Request",
        success: false,
        message: `Error: ${error instanceof Error ? error.message : String(error)}`,
      });
      setIsRunning(false);
    }
  };

  // Step 7: Test device authentication WITHOUT JWT
  const testDeviceAuthWithoutJwt = async () => {
    if (!deviceCredential || !authReqId) return;

    try {
      const response = await fetch(`/api/fido-uaf-device-test?action=device-authentications`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          deviceId: deviceCredential.deviceId,
          authReqId,
          // No deviceSecretJwt - testing unauthenticated access
        }),
      });
      const data = await response.json();

      const hasContext = data.data?.list?.[0]?.context !== undefined;
      setDeviceAuthWithoutJwt({
        status: data.status,
        hasContext,
      });

      addResult({
        step: "Device Auth (No JWT)",
        success: data.status === 401 || data.status === 200,
        message:
          data.status === 401
            ? "401 Unauthorized - JWT required (expected behavior)"
            : `200 OK - context ${hasContext ? "included" : "excluded"}`,
        details: {
          status: data.status,
          hasContext,
          authenticated: false,
        },
      });
      setCurrentStep("device-auth-without-jwt");
    } catch (error) {
      addResult({
        step: "Device Auth (No JWT)",
        success: false,
        message: `Error: ${error instanceof Error ? error.message : String(error)}`,
      });
      setIsRunning(false);
    }
  };

  // Step 8: Test device authentication WITH JWT
  const testDeviceAuthWithJwt = async () => {
    if (!deviceCredential || !authReqId) return;

    try {
      // Create JWT using device_secret
      const jwt = await createDeviceSecretJwt(deviceCredential);

      const response = await fetch(`/api/fido-uaf-device-test?action=device-authentications`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          deviceId: deviceCredential.deviceId,
          authReqId,
          deviceSecretJwt: jwt,
        }),
      });
      const data = await response.json();

      const hasContext = data.data?.list?.[0]?.context !== undefined;
      setDeviceAuthWithJwt({
        status: data.status,
        hasContext,
      });

      addResult({
        step: "Device Auth (With JWT)",
        success: data.status === 200 && hasContext,
        message: `200 OK - context ${hasContext ? "INCLUDED" : "missing"} (device authenticated)`,
        details: {
          status: data.status,
          hasContext,
          authenticated: true,
          context: data.data?.list?.[0]?.context,
        },
      });
      setCurrentStep("device-auth-with-jwt");
    } catch (error) {
      addResult({
        step: "Device Auth (With JWT)",
        success: false,
        message: `Error: ${error instanceof Error ? error.message : String(error)}`,
      });
      setIsRunning(false);
    }
  };

  // Step 9: JWT Bearer Grant
  const testJwtBearerGrant = async () => {
    if (!deviceCredential) return;

    try {
      const jwt = await createDeviceSecretJwt(deviceCredential);

      // Decode JWT for debugging (header.payload.signature)
      const [headerB64, payloadB64] = jwt.split(".");
      const header = JSON.parse(atob(headerB64.replace(/-/g, "+").replace(/_/g, "/")));
      const payload = JSON.parse(atob(payloadB64.replace(/-/g, "+").replace(/_/g, "/")));
      console.log("JWT Header:", header);
      console.log("JWT Payload:", payload);

      const response = await fetch(`/api/fido-uaf-device-test?action=jwt-bearer-grant`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          assertion: jwt,
          scope: "openid profile email",
        }),
      });
      const data = await response.json();

      setJwtBearerGrantResult({
        success: data.success,
        accessToken: data.data?.access_token,
      });

      addResult({
        step: "JWT Bearer Grant",
        success: data.success,
        message: data.success
          ? "Access token obtained via JWT Bearer Grant"
          : "JWT Bearer Grant failed",
        details: {
          jwtHeader: header,
          jwtPayload: payload,
          hasAccessToken: !!data.data?.access_token,
          tokenType: data.data?.token_type,
          error: data.data?.error,
          errorDescription: data.data?.error_description,
        },
      });
      setCurrentStep("completed");
      setIsRunning(false);
    } catch (error) {
      addResult({
        step: "JWT Bearer Grant",
        success: false,
        message: `Error: ${error instanceof Error ? error.message : String(error)}`,
      });
      setIsRunning(false);
    }
  };

  // Create JWT with device_secret using jose library
  const createDeviceSecretJwt = async (credential: DeviceCredential): Promise<string> => {
    // Import secret as UTF-8 bytes (same as server's MACVerifier)
    const secret = new TextEncoder().encode(credential.deviceSecret);

    // Use deviceId as subject - server will lookup user by device ownership
    const jwt = await new jose.SignJWT({})
      .setProtectedHeader({ alg: credential.deviceSecretAlgorithm, typ: "JWT" })
      .setIssuer(credential.deviceSecretJwtIssuer)
      .setSubject(credential.deviceId)
      .setAudience(process.env.NEXT_PUBLIC_IDP_SERVER_ISSUER || window.location.origin)
      .setJti(crypto.randomUUID())
      .setIssuedAt()
      .setExpirationTime("5m")
      .sign(secret);

    return jwt;
  };

  // Run full demo
  const runFullDemo = async () => {
    await startAuthorization();
  };

  // Continue demo based on current step
  useEffect(() => {
    if (!isRunning || !authorizationId) return;

    const continueDemo = async () => {
      switch (currentStep) {
        case "start":
          await registerUser();
          break;
        case "user-registered":
          await getFidoUafChallenge();
          break;
        case "fido-uaf-challenged":
          await completeFidoUafRegistration();
          break;
        case "device-registered":
          await completeAuthorization();
          break;
        case "authorized":
          await startCibaRequest();
          break;
        case "ciba-requested":
          await testDeviceAuthWithoutJwt();
          break;
        case "device-auth-without-jwt":
          await testDeviceAuthWithJwt();
          break;
        case "device-auth-with-jwt":
          await testJwtBearerGrant();
          break;
      }
    };

    const timer = setTimeout(continueDemo, 500);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentStep, isRunning, authorizationId]);

  const steps = [
    "Authorization Start",
    "User Registration",
    "FIDO-UAF Challenge",
    "Device Registration",
    "Authorization Complete",
    "CIBA Request",
    "Device Auth (No JWT)",
    "Device Auth (With JWT)",
    "JWT Bearer Grant",
  ];

  const getActiveStep = () => {
    const stepMap: Record<DemoStep, number> = {
      start: 0,
      "user-registered": 1,
      "fido-uaf-challenged": 2,
      "device-registered": 3,
      authorized: 4,
      "ciba-requested": 5,
      "device-auth-without-jwt": 6,
      "device-auth-with-jwt": 7,
      "jwt-bearer-grant": 8,
      completed: 9,
    };
    return stepMap[currentStep];
  };

  return (
    <Container maxWidth="lg" sx={{ py: 4 }}>
      <Stack spacing={4}>
        {/* Header */}
        <Card>
          <CardContent>
            <Stack direction="row" alignItems="center" spacing={2} sx={{ mb: 2 }}>
              <PhoneAndroidIcon sx={{ fontSize: 48, color: "success.main" }} />
              <Box>
                <Typography variant="h4" component="h1">
                  FIDO-UAF Device Credential Demo
                </Typography>
                <Typography variant="body1" color="text.secondary">
                  device_secret issuance, device_secret_jwt authentication, JWT Bearer Grant
                </Typography>
              </Box>
            </Stack>

            <Alert severity="info" sx={{ mt: 2 }}>
              <AlertTitle>Demo Overview</AlertTitle>
              <Typography variant="body2">
                This demo shows the full device credential flow:
              </Typography>
              <ol style={{ margin: "8px 0", paddingLeft: "20px" }}>
                <li>
                  <strong>FIDO-UAF Registration</strong>: Register device and receive device_secret
                </li>
                <li>
                  <strong>Device Endpoint Authentication</strong>: Compare responses with/without
                  device_secret_jwt
                </li>
                <li>
                  <strong>JWT Bearer Grant</strong>: Exchange device_secret JWT for access token
                </li>
              </ol>
            </Alert>
          </CardContent>
        </Card>

        {/* Feature Comparison */}
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              <SecurityIcon sx={{ mr: 1, verticalAlign: "middle" }} />
              Device Authentication Response Control
            </Typography>
            <TableContainer component={Paper} variant="outlined">
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>
                      <strong>Authentication Type</strong>
                    </TableCell>
                    <TableCell>
                      <strong>context Field</strong>
                    </TableCell>
                    <TableCell>
                      <strong>Description</strong>
                    </TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  <TableRow>
                    <TableCell>
                      <Chip label="none" size="small" color="default" />
                    </TableCell>
                    <TableCell>
                      <Chip
                        icon={<LockOpenIcon />}
                        label="Excluded"
                        size="small"
                        color="warning"
                      />
                    </TableCell>
                    <TableCell>No authentication required, sensitive info excluded</TableCell>
                  </TableRow>
                  <TableRow sx={{ backgroundColor: "success.light" }}>
                    <TableCell>
                      <Chip label="device_secret_jwt" size="small" color="success" />
                    </TableCell>
                    <TableCell>
                      <Chip icon={<LockIcon />} label="Included" size="small" color="success" />
                    </TableCell>
                    <TableCell>
                      <strong>HMAC JWT authentication - full context returned</strong>
                    </TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell>
                      <Chip label="private_key_jwt" size="small" color="primary" />
                    </TableCell>
                    <TableCell>
                      <Chip icon={<LockIcon />} label="Included" size="small" color="success" />
                    </TableCell>
                    <TableCell>RSA/EC JWT authentication - full context returned</TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell>
                      <Chip label="access_token" size="small" color="secondary" />
                    </TableCell>
                    <TableCell>
                      <Chip icon={<LockIcon />} label="Included" size="small" color="success" />
                    </TableCell>
                    <TableCell>Bearer token authentication - full context returned</TableCell>
                  </TableRow>
                </TableBody>
              </Table>
            </TableContainer>
          </CardContent>
        </Card>

        {/* Algorithm Selection */}
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              <KeyIcon sx={{ mr: 1, verticalAlign: "middle" }} />
              Secret Algorithm (OIDC Core Section 16.19)
            </Typography>
            <ToggleButtonGroup
              value={algorithm}
              exclusive
              onChange={(_, value) => value && setAlgorithm(value)}
              sx={{ mb: 2 }}
            >
              <ToggleButton value="HS256">
                HS256 ({ALGORITHM_INFO.HS256.bits} bits / {ALGORITHM_INFO.HS256.bytes} bytes)
              </ToggleButton>
              <ToggleButton value="HS384">
                HS384 ({ALGORITHM_INFO.HS384.bits} bits / {ALGORITHM_INFO.HS384.bytes} bytes)
              </ToggleButton>
              <ToggleButton value="HS512">
                HS512 ({ALGORITHM_INFO.HS512.bits} bits / {ALGORITHM_INFO.HS512.bytes} bytes)
              </ToggleButton>
            </ToggleButtonGroup>
            <Alert severity="warning" variant="outlined">
              Note: Algorithm is configured in tenant policy (authentication_device_rule.device_secret_algorithm).
              This demo uses the tenant&apos;s configured algorithm.
            </Alert>
          </CardContent>
        </Card>

        {/* User Input */}
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              <DevicesIcon sx={{ mr: 1, verticalAlign: "middle" }} />
              Demo User Configuration
            </Typography>
            <Stack direction="row" spacing={2} sx={{ mb: 2 }}>
              <TextField
                label="Email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                size="small"
                fullWidth
                disabled={isRunning}
              />
              <TextField
                label="Username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                size="small"
                fullWidth
                disabled={isRunning}
              />
              <TextField
                label="Password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                size="small"
                fullWidth
                disabled={isRunning}
                type="password"
              />
            </Stack>
            <Stack direction="row" spacing={2}>
              <Button
                variant="contained"
                color="success"
                startIcon={isRunning ? <CircularProgress size={20} /> : <PlayArrowIcon />}
                onClick={runFullDemo}
                disabled={isRunning}
              >
                {isRunning ? "Running..." : "Run Full Demo"}
              </Button>
              <Button
                variant="outlined"
                startIcon={<RestartAltIcon />}
                onClick={resetTest}
                disabled={isRunning}
              >
                Reset
              </Button>
              <Button variant="outlined" startIcon={<HomeIcon />} href="/">
                Home
              </Button>
            </Stack>
          </CardContent>
        </Card>

        {/* Progress Stepper */}
        {testResults.length > 0 && (
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Demo Progress
              </Typography>
              <Stepper activeStep={getActiveStep()} alternativeLabel>
                {steps.map((label) => (
                  <Step key={label}>
                    <StepLabel>{label}</StepLabel>
                  </Step>
                ))}
              </Stepper>
            </CardContent>
          </Card>
        )}

        {/* Device Credential Info */}
        {deviceCredential && (
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                <VpnKeyIcon sx={{ mr: 1, verticalAlign: "middle", color: "success.main" }} />
                Issued Device Credential
              </Typography>
              <TableContainer component={Paper} variant="outlined">
                <Table size="small">
                  <TableBody>
                    <TableRow>
                      <TableCell>
                        <strong>Device ID</strong>
                      </TableCell>
                      <TableCell>
                        <code>{deviceCredential.deviceId}</code>
                      </TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell>
                        <strong>Algorithm</strong>
                      </TableCell>
                      <TableCell>
                        <Chip label={deviceCredential.deviceSecretAlgorithm} color="primary" size="small" />
                      </TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell>
                        <strong>Secret Length</strong>
                      </TableCell>
                      <TableCell>
                        {deviceCredential.deviceSecret.length} characters (
                        {new TextEncoder().encode(deviceCredential.deviceSecret).length} bytes)
                      </TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell>
                        <strong>JWT Issuer</strong>
                      </TableCell>
                      <TableCell>
                        <code>{deviceCredential.deviceSecretJwtIssuer}</code>
                      </TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell>
                        <strong>Device Secret</strong>
                      </TableCell>
                      <TableCell>
                        <code style={{ wordBreak: "break-all" }}>
                          {deviceCredential.deviceSecret.substring(0, 20)}...
                        </code>
                      </TableCell>
                    </TableRow>
                  </TableBody>
                </Table>
              </TableContainer>
            </CardContent>
          </Card>
        )}

        {/* Comparison Results */}
        {(deviceAuthWithoutJwt || deviceAuthWithJwt) && (
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                <SecurityIcon sx={{ mr: 1, verticalAlign: "middle" }} />
                Device Authentication Comparison
              </Typography>
              <TableContainer component={Paper} variant="outlined">
                <Table>
                  <TableHead>
                    <TableRow>
                      <TableCell>
                        <strong>Scenario</strong>
                      </TableCell>
                      <TableCell>
                        <strong>Status</strong>
                      </TableCell>
                      <TableCell>
                        <strong>context Field</strong>
                      </TableCell>
                      <TableCell>
                        <strong>Result</strong>
                      </TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {deviceAuthWithoutJwt && (
                      <TableRow>
                        <TableCell>Without device_secret_jwt</TableCell>
                        <TableCell>
                          <Chip
                            label={deviceAuthWithoutJwt.status}
                            color={deviceAuthWithoutJwt.status === 401 ? "error" : "success"}
                            size="small"
                          />
                        </TableCell>
                        <TableCell>
                          {deviceAuthWithoutJwt.status === 401 ? (
                            <Chip label="N/A (401)" size="small" />
                          ) : (
                            <Chip
                              icon={deviceAuthWithoutJwt.hasContext ? <LockIcon /> : <LockOpenIcon />}
                              label={deviceAuthWithoutJwt.hasContext ? "Included" : "Excluded"}
                              color={deviceAuthWithoutJwt.hasContext ? "success" : "warning"}
                              size="small"
                            />
                          )}
                        </TableCell>
                        <TableCell>
                          {deviceAuthWithoutJwt.status === 401
                            ? "Authentication required"
                            : "Sensitive info protected"}
                        </TableCell>
                      </TableRow>
                    )}
                    {deviceAuthWithJwt && (
                      <TableRow sx={{ backgroundColor: "success.light" }}>
                        <TableCell>
                          <strong>With device_secret_jwt</strong>
                        </TableCell>
                        <TableCell>
                          <Chip
                            label={deviceAuthWithJwt.status}
                            color="success"
                            size="small"
                          />
                        </TableCell>
                        <TableCell>
                          <Chip
                            icon={<LockIcon />}
                            label={deviceAuthWithJwt.hasContext ? "Included" : "Missing"}
                            color={deviceAuthWithJwt.hasContext ? "success" : "error"}
                            size="small"
                          />
                        </TableCell>
                        <TableCell>
                          <strong>Full context returned to authenticated device</strong>
                        </TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
              </TableContainer>
            </CardContent>
          </Card>
        )}

        {/* JWT Bearer Grant Result */}
        {jwtBearerGrantResult && (
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                <TokenIcon sx={{ mr: 1, verticalAlign: "middle" }} />
                JWT Bearer Grant Result
              </Typography>
              <Alert severity={jwtBearerGrantResult.success ? "success" : "error"}>
                <AlertTitle>
                  {jwtBearerGrantResult.success ? "Success" : "Failed"}
                </AlertTitle>
                {jwtBearerGrantResult.success
                  ? "Access token successfully obtained using device_secret JWT assertion"
                  : "Failed to obtain access token"}
              </Alert>
              {jwtBearerGrantResult.accessToken && (
                <Box sx={{ mt: 2 }}>
                  <Typography variant="subtitle2">Access Token (truncated):</Typography>
                  <Paper variant="outlined" sx={{ p: 1, mt: 1 }}>
                    <code style={{ wordBreak: "break-all", fontSize: "12px" }}>
                      {jwtBearerGrantResult.accessToken.substring(0, 100)}...
                    </code>
                  </Paper>
                </Box>
              )}
            </CardContent>
          </Card>
        )}

        {/* Execution Log */}
        {testResults.length > 0 && (
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Execution Log
              </Typography>
              <Stepper orientation="vertical" activeStep={testResults.length}>
                {testResults.map((result, index) => (
                  <Step key={index} completed>
                    <StepLabel
                      icon={
                        result.success ? (
                          <CheckCircleIcon color="success" />
                        ) : (
                          <ErrorIcon color="error" />
                        )
                      }
                    >
                      <Typography variant="subtitle2">{result.step}</Typography>
                    </StepLabel>
                    <Box sx={{ ml: 4, mb: 2 }}>
                      <Alert
                        severity={result.success ? "success" : "error"}
                        variant="outlined"
                        sx={{ py: 0 }}
                      >
                        {result.message}
                      </Alert>
                      {result.details && (
                        <Paper variant="outlined" sx={{ p: 1, mt: 1, backgroundColor: "grey.50" }}>
                          <pre style={{ margin: 0, fontSize: "11px", overflow: "auto" }}>
                            {JSON.stringify(result.details, null, 2)}
                          </pre>
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

