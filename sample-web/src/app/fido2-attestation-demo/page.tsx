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
  ToggleButton,
  ToggleButtonGroup,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Tooltip,
} from "@mui/material";
import { useState, useEffect } from "react";
import FingerprintIcon from "@mui/icons-material/Fingerprint";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import ErrorIcon from "@mui/icons-material/Error";
import InfoIcon from "@mui/icons-material/Info";
import SecurityIcon from "@mui/icons-material/Security";
import PlayArrowIcon from "@mui/icons-material/PlayArrow";
import RestartAltIcon from "@mui/icons-material/RestartAlt";
import PhoneIphoneIcon from "@mui/icons-material/PhoneIphone";
import UsbIcon from "@mui/icons-material/Usb";
import EmailIcon from "@mui/icons-material/Email";
import CompareArrowsIcon from "@mui/icons-material/CompareArrows";

/**
 * Convert Base64URL string to ArrayBuffer
 */
const base64UrlToBuffer = (base64url: string): ArrayBuffer => {
  const base64 = base64url.replace(/-/g, "+").replace(/_/g, "/");
  const binaryString = atob(base64);
  const bytes = Uint8Array.from(binaryString, (char) => char.charCodeAt(0));
  return bytes.buffer as ArrayBuffer;
};

/**
 * Convert ArrayBuffer to Base64URL string
 */
const bufferToBase64Url = (buffer: ArrayBuffer): string => {
  const bytes = new Uint8Array(buffer);
  let binary = "";
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  const base64 = btoa(binary);
  return base64.replace(/\+/g, "-").replace(/\//g, "_").replace(/=/g, "");
};

interface ChallengeResponse {
  challenge: string;
  timeout?: number;
  rp?: {
    id?: string;
    name?: string;
  };
  user?: {
    id: string;
    name: string;
    displayName: string;
  };
  pubKeyCredParams?: { type: string; alg: number }[];
  attestation?: string;
  authenticatorSelection?: {
    authenticatorAttachment?: string;
    requireResidentKey?: boolean;
    residentKey?: string;
    userVerification?: string;
  };
}

interface TestResult {
  step: string;
  success: boolean;
  message: string;
  details?: Record<string, unknown>;
}

interface RegistrationResult {
  authenticatorType: "platform" | "cross-platform";
  attestationPreference: string;
  format: string;
  aaguid: string;
  credentialId: string;
  transports: string[];
  timestamp: Date;
}

// Demo flow steps
type DemoStep = "start" | "user-info" | "email-sent" | "email-verified" | "fido2-ready" | "completed";

// Authenticator type
type AuthenticatorType = "platform" | "cross-platform";

// Attestation preference
type AttestationPreference = "none" | "indirect" | "direct" | "enterprise";

// Known AAGUIDs for passkey providers
// Source: https://github.com/passkeydeveloper/passkey-authenticator-aaguids
const KNOWN_AAGUIDS: Record<string, string> = {
  "fbfc3007-154e-4ecc-8c0b-6e020557d7bd": "iCloud Keychain",
  "dd4ec289-e01d-41c9-bb89-70fa845d4bf2": "iCloud Keychain (Managed)",
  "ea9b8d66-4d01-1d21-3ce4-b6b48cb575d4": "Google Password Manager",
  "adce0002-35bc-c60a-648b-0b25f1f05503": "Chrome on Mac",
  "08987058-cadc-4b81-b6e1-30de50dcbe96": "Windows Hello",
  "9ddd1817-af5a-4672-a2b9-3e3dd95000a9": "Windows Hello",
  "6028b017-b1d4-4c02-b4b3-afcdafc96bb2": "Windows Hello",
  "bada5566-a7aa-401f-bd96-45619a55120d": "1Password",
  "d548826e-79b4-db40-a3d8-11116f7e8349": "Bitwarden",
  "531126d6-e717-415c-9320-3d9aa6981239": "Dashlane",
  "771b48fd-d3d4-4f74-9232-fc157ab0507a": "Edge on Mac",
  "00000000-0000-0000-0000-000000000000": "Unknown (Zero AAGUID)",
};

const lookupAaguid = (aaguid: string): string | null => {
  return KNOWN_AAGUIDS[aaguid.toLowerCase()] || null;
};

export default function Fido2RegistrationDemoPage() {
  const [email, setEmail] = useState<string>("");
  const [username, setUsername] = useState<string>("");
  const [password, setPassword] = useState<string>("");
  const [verificationCode, setVerificationCode] = useState<string>("");

  // Demo flow state
  const [currentStep, setCurrentStep] = useState<DemoStep>("start");
  const [isRunning, setIsRunning] = useState(false);
  const [authorizationId, setAuthorizationId] = useState<string | null>(null);
  const [challengeData, setChallengeData] = useState<ChallengeResponse | null>(null);
  const [testResults, setTestResults] = useState<TestResult[]>([]);

  // Registration options
  const [authenticatorType, setAuthenticatorType] = useState<AuthenticatorType>("platform");
  const [attestationPreference, setAttestationPreference] = useState<AttestationPreference>("direct");

  // Registration history for comparison
  const [registrationHistory, setRegistrationHistory] = useState<RegistrationResult[]>([]);

  useEffect(() => {
    if (typeof window !== "undefined") {
      const timestamp = Date.now();
      setEmail(`demo-${timestamp}@example.com`);
      setUsername(`デモユーザー ${timestamp}`);
      setPassword(`DemoPass${timestamp}!`);
    }
  }, []);

  const addResult = (result: TestResult) => {
    setTestResults((prev) => [...prev, result]);
  };

  const resetTest = () => {
    setAuthorizationId(null);
    setChallengeData(null);
    setTestResults([]);
    setCurrentStep("start");
    setVerificationCode("");
    const timestamp = Date.now();
    setEmail(`demo-${timestamp}@example.com`);
    setUsername(`デモユーザー ${timestamp}`);
    setPassword(`DemoPass${timestamp}!`);
  };

  const clearHistory = () => {
    setRegistrationHistory([]);
  };

  // Step 1: Start authorization request
  const startAuthorization = async () => {
    setIsRunning(true);
    setTestResults([]);

    try {
      const response = await fetch("/api/fido2-attestation-test?action=start", {
        credentials: "include",
      });
      const data = await response.json();

      if (data.success && data.authorizationId) {
        setAuthorizationId(data.authorizationId);
        setCurrentStep("user-info");
        addResult({
          step: "Step 1: 認可リクエスト開始",
          success: true,
          message: `authorization_id を取得しました`,
          details: {
            authorizationId: data.authorizationId,
          },
        });
      } else {
        addResult({
          step: "Step 1: 認可リクエスト開始",
          success: false,
          message: "認可リクエストの開始に失敗しました",
          details: data,
        });
      }
    } catch (error) {
      addResult({
        step: "Step 1: 認可リクエスト開始",
        success: false,
        message: error instanceof Error ? error.message : "エラーが発生しました",
      });
    } finally {
      setIsRunning(false);
    }
  };

  // Step 2: Initial registration
  const doInitialRegistration = async () => {
    if (!authorizationId || !email || !password || !username) return;

    setIsRunning(true);

    try {
      const response = await fetch(
        `/api/fido2-attestation-test?action=initial-registration&authorizationId=${authorizationId}`,
        {
          method: "POST",
          credentials: "include",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            email,
            password,
            name: username,
          }),
        }
      );

      const data = await response.json();

      if (data.success) {
        addResult({
          step: "Step 2: ユーザー登録",
          success: true,
          message: `ユーザーを登録しました`,
        });

        await requestEmailChallenge();
      } else {
        addResult({
          step: "Step 2: ユーザー登録",
          success: false,
          message: `ユーザー登録に失敗しました (${data.status})`,
          details: data.data,
        });
      }
    } catch (error) {
      addResult({
        step: "Step 2: ユーザー登録",
        success: false,
        message: error instanceof Error ? error.message : "エラーが発生しました",
      });
    } finally {
      setIsRunning(false);
    }
  };

  // Step 3: Request email challenge
  const requestEmailChallenge = async () => {
    if (!authorizationId || !email) return;

    try {
      const response = await fetch(
        `/api/fido2-attestation-test?action=email-challenge&authorizationId=${authorizationId}`,
        {
          method: "POST",
          credentials: "include",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ email }),
        }
      );

      const data = await response.json();

      if (data.success) {
        setCurrentStep("email-sent");
        addResult({
          step: "Step 3: メール認証チャレンジ",
          success: true,
          message: `認証コードを ${email} に送信しました`,
        });
      } else {
        addResult({
          step: "Step 3: メール認証チャレンジ",
          success: false,
          message: `メール認証チャレンジの送信に失敗しました`,
          details: data.data,
        });
      }
    } catch (error) {
      addResult({
        step: "Step 3: メール認証チャレンジ",
        success: false,
        message: error instanceof Error ? error.message : "エラーが発生しました",
      });
    }
  };

  // Step 4: Verify email
  const verifyEmail = async () => {
    if (!authorizationId || !verificationCode) return;

    setIsRunning(true);

    try {
      const response = await fetch(
        `/api/fido2-attestation-test?action=email-verify&authorizationId=${authorizationId}`,
        {
          method: "POST",
          credentials: "include",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ verification_code: verificationCode }),
        }
      );

      const data = await response.json();

      if (data.success) {
        setCurrentStep("email-verified");
        addResult({
          step: "Step 4: メール認証完了",
          success: true,
          message: "メールアドレスの認証が完了しました",
        });
      } else {
        addResult({
          step: "Step 4: メール認証完了",
          success: false,
          message: `メール認証に失敗しました`,
          details: data.data,
        });
      }
    } catch (error) {
      addResult({
        step: "Step 4: メール認証完了",
        success: false,
        message: error instanceof Error ? error.message : "エラーが発生しました",
      });
    } finally {
      setIsRunning(false);
    }
  };

  // Step 5: Get registration challenge
  const getRegistrationChallenge = async () => {
    if (!authorizationId) return;

    setIsRunning(true);

    try {
      const response = await fetch(
        `/api/fido2-attestation-test?action=registration-challenge&authorizationId=${authorizationId}&username=${encodeURIComponent(email)}&displayName=${encodeURIComponent(username)}`,
        {
          credentials: "include",
        }
      );

      const data = await response.json();

      if (data.success) {
        const challengeResponse: ChallengeResponse = data.challenge;
        setChallengeData(challengeResponse);
        setCurrentStep("fido2-ready");

        addResult({
          step: "Step 5: FIDO2登録チャレンジ取得",
          success: true,
          message: `登録チャレンジを取得しました`,
          details: {
            rpId: challengeResponse.rp?.id,
            serverAttestation: challengeResponse.attestation,
          },
        });
      } else {
        addResult({
          step: "Step 5: FIDO2登録チャレンジ取得",
          success: false,
          message: `チャレンジ取得に失敗しました`,
          details: data,
        });
      }
    } catch (error) {
      addResult({
        step: "Step 5: FIDO2登録チャレンジ取得",
        success: false,
        message: error instanceof Error ? error.message : "エラーが発生しました",
      });
    } finally {
      setIsRunning(false);
    }
  };

  // Step 6: Execute WebAuthn registration
  const executeRegistration = async () => {
    if (!challengeData || !authorizationId) return;

    setIsRunning(true);

    try {
      const {
        challenge,
        rp = { name: "Demo RP" },
        user,
        pubKeyCredParams,
        timeout = 60000,
      } = challengeData;

      if (!user) {
        throw new Error("チャレンジにユーザー情報が含まれていません");
      }

      const rpEntity: PublicKeyCredentialRpEntity = {
        id: rp.id,
        name: rp.name || "Demo RP",
      };

      // Build PublicKeyCredentialCreationOptions with user-selected options
      const publicKeyOptions: PublicKeyCredentialCreationOptions = {
        challenge: base64UrlToBuffer(challenge),
        rp: rpEntity,
        user: {
          id: base64UrlToBuffer(user.id),
          name: user.name,
          displayName: user.displayName,
        },
        authenticatorSelection: {
          authenticatorAttachment: authenticatorType,
          requireResidentKey: true,
          residentKey: "required",
          userVerification: "required",
        },
        pubKeyCredParams: (pubKeyCredParams || [
          { type: "public-key", alg: -7 },
          { type: "public-key", alg: -257 },
        ]) as PublicKeyCredentialParameters[],
        timeout,
        attestation: attestationPreference as AttestationConveyancePreference,
        extensions: {
          credProps: true,
        },
      };

      addResult({
        step: "Step 6: WebAuthn登録実行",
        success: true,
        message: `navigator.credentials.create() を呼び出します`,
        details: {
          authenticatorAttachment: authenticatorType,
          attestationRequested: attestationPreference,
          rpId: rp.id,
        },
      });

      // Execute WebAuthn registration
      const credential = (await navigator.credentials.create({
        publicKey: publicKeyOptions,
      })) as PublicKeyCredential;

      if (!credential) {
        throw new Error("認証器からクレデンシャルを取得できませんでした");
      }

      const attestationResponse = credential.response as AuthenticatorAttestationResponse;

      // Serialize credential
      const credentialData = {
        id: credential.id,
        rawId: bufferToBase64Url(credential.rawId),
        type: credential.type,
        response: {
          clientDataJSON: bufferToBase64Url(attestationResponse.clientDataJSON),
          attestationObject: bufferToBase64Url(attestationResponse.attestationObject),
          transports: attestationResponse.getTransports ? attestationResponse.getTransports() : [],
        },
        clientExtensionResults: credential.getClientExtensionResults(),
      };

      // Parse attestation information
      const attestationObjectBytes = new Uint8Array(attestationResponse.attestationObject);

      let attestationFormat = "unknown";
      try {
        const attestationHex = Array.from(attestationObjectBytes, (b) => b.toString(16).padStart(2, "0")).join("");
        if (attestationHex.includes("636e6f6e65") || attestationHex.includes("646e6f6e65")) {
          attestationFormat = "none";
        } else if (attestationHex.includes("667061636b6564") || attestationHex.includes("6670616b6564")) {
          attestationFormat = "packed";
        } else if (attestationHex.includes("6966696f2d753266")) {
          attestationFormat = "fido-u2f";
        } else if (attestationHex.includes("6574706d")) {
          attestationFormat = "tpm";
        } else if (attestationHex.includes("656170706c65")) {
          attestationFormat = "apple";
        }
        // Additional detection using simple markers
        const fmtIndex = attestationHex.indexOf("63666d74");
        if (fmtIndex > 0) {
          const afterFmt = attestationHex.slice(fmtIndex + 8, fmtIndex + 30);
          if (afterFmt.includes("6e6f6e65")) attestationFormat = "none";
          else if (afterFmt.includes("7061636b6564")) attestationFormat = "packed";
          else if (afterFmt.includes("6170706c65")) attestationFormat = "apple";
        }
      } catch {
        attestationFormat = "parse error";
      }

      // Parse AAGUID
      let aaguid = "00000000-0000-0000-0000-000000000000";
      try {
        for (let i = 0; i < attestationObjectBytes.length - 53; i++) {
          const potentialFlags = attestationObjectBytes[i + 32];
          if ((potentialFlags & 0x40) === 0x40) {
            const aaguidBytes = attestationObjectBytes.slice(i + 37, i + 53);
            const hex = Array.from(aaguidBytes, (b) => b.toString(16).padStart(2, "0")).join("");
            aaguid = `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
            break;
          }
        }
      } catch {
        aaguid = "parse error";
      }

      // Add to history for comparison
      const result: RegistrationResult = {
        authenticatorType,
        attestationPreference,
        format: attestationFormat,
        aaguid,
        credentialId: credential.id,
        transports: credentialData.response.transports || [],
        timestamp: new Date(),
      };
      setRegistrationHistory((prev) => [result, ...prev]);

      const providerName = lookupAaguid(aaguid);
      addResult({
        step: "Step 7: クレデンシャル作成完了",
        success: true,
        message: providerName
          ? `「${providerName}」がアテステーション形式「${attestationFormat}」でクレデンシャルを返しました`
          : `認証器がアテステーション形式「${attestationFormat}」でクレデンシャルを返しました`,
        details: {
          credentialId: credential.id.substring(0, 30) + "...",
          attestationFormat,
          aaguid,
          provider: providerName || "Unknown",
          transports: credentialData.response.transports,
          authenticatorType,
          attestationRequested: attestationPreference,
        },
      });

      // Submit to server
      const registerResponse = await fetch(
        `/api/fido2-attestation-test?action=register&authorizationId=${authorizationId}`,
        {
          method: "POST",
          credentials: "include",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(credentialData),
        }
      );

      const registerResult = await registerResponse.json();

      if (registerResult.success) {
        setCurrentStep("completed");
        addResult({
          step: "Step 8: サーバー登録完了",
          success: true,
          message: "FIDO2クレデンシャルがサーバーに正常に登録されました",
          details: registerResult.data,
        });
      } else {
        addResult({
          step: "Step 8: サーバー登録",
          success: false,
          message: `サーバーが登録を拒否しました`,
          details: registerResult.data,
        });
      }
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : "エラーが発生しました";
      const errorName = error instanceof Error ? error.name : "Unknown";

      addResult({
        step: "登録失敗",
        success: false,
        message: errorMessage,
        details: {
          errorName,
          hint:
            errorName === "NotAllowedError"
              ? "ユーザーがキャンセルしたか、指定した認証器が利用できません"
              : errorName === "InvalidStateError"
              ? "この認証器は既に登録されています"
              : errorName === "NotSupportedError"
              ? "この認証器タイプはサポートされていません"
              : undefined,
        },
      });
    } finally {
      setIsRunning(false);
    }
  };

  const getActiveStep = (): number => {
    switch (currentStep) {
      case "start": return 0;
      case "user-info": return 1;
      case "email-sent": return 2;
      case "email-verified": return 3;
      case "fido2-ready": return 4;
      case "completed": return 5;
      default: return 0;
    }
  };

  const demoSteps = ["認可開始", "ユーザー登録", "メール認証", "認証完了", "FIDO2登録", "完了"];

  return (
    <Container maxWidth="lg" sx={{ py: 4 }}>
      <Stack spacing={4}>
        {/* Header */}
        <Stack direction="row" alignItems="center" spacing={2}>
          <FingerprintIcon sx={{ fontSize: 40, color: "primary.main" }} />
          <Box>
            <Typography variant="h4" component="h1">
              FIDO2 登録デモ
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Platform/Cross-platform認証器とアテステーション設定の違いを確認できます
            </Typography>
          </Box>
        </Stack>

        {/* Authenticator Type Comparison */}
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              <CompareArrowsIcon sx={{ mr: 1, verticalAlign: "middle" }} />
              認証器タイプの比較
            </Typography>
            <TableContainer component={Paper} variant="outlined">
              <Table>
                <TableHead>
                  <TableRow sx={{ bgcolor: "grey.100" }}>
                    <TableCell><strong>タイプ</strong></TableCell>
                    <TableCell><strong>例</strong></TableCell>
                    <TableCell><strong>アテステーション</strong></TableCell>
                    <TableCell><strong>AAGUID</strong></TableCell>
                    <TableCell><strong>特徴</strong></TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  <TableRow>
                    <TableCell>
                      <Chip
                        icon={<PhoneIphoneIcon />}
                        label="Platform"
                        color="primary"
                        size="small"
                      />
                    </TableCell>
                    <TableCell>iCloud Keychain, Google Password Manager, Windows Hello</TableCell>
                    <TableCell>
                      <Chip label="none" size="small" color="default" />
                      <Typography variant="caption" display="block" color="text.secondary">
                        プライバシー保護のため
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Chip label="プロバイダー固有" size="small" color="info" />
                      <Typography variant="caption" display="block" color="text.secondary">
                        識別可能（下表参照）
                      </Typography>
                    </TableCell>
                    <TableCell>便利、デバイスに組み込み</TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell>
                      <Chip
                        icon={<UsbIcon />}
                        label="Cross-platform"
                        color="secondary"
                        size="small"
                      />
                    </TableCell>
                    <TableCell>YubiKey, Titan Key, Feitian</TableCell>
                    <TableCell>
                      <Chip label="packed" size="small" color="success" />
                      <Typography variant="caption" display="block" color="text.secondary">
                        完全なアテステーション
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Chip label="ベンダー固有" size="small" color="success" />
                      <Typography variant="caption" display="block" color="text.secondary">
                        FIDO MDS検索可能
                      </Typography>
                    </TableCell>
                    <TableCell>持ち運び可能、高セキュリティ</TableCell>
                  </TableRow>
                </TableBody>
              </Table>
            </TableContainer>

            {/* Known AAGUIDs */}
            <Typography variant="subtitle2" fontWeight="bold" sx={{ mt: 3, mb: 1 }}>
              主要パスキープロバイダーのAAGUID一覧
            </Typography>
            <TableContainer component={Paper} variant="outlined">
              <Table size="small">
                <TableHead>
                  <TableRow sx={{ bgcolor: "grey.50" }}>
                    <TableCell><strong>プロバイダー</strong></TableCell>
                    <TableCell><strong>AAGUID</strong></TableCell>
                    <TableCell><strong>タイプ</strong></TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  <TableRow>
                    <TableCell>iCloud Keychain (Apple Passwords)</TableCell>
                    <TableCell><code style={{ fontSize: "0.75rem" }}>fbfc3007-154e-4ecc-8c0b-6e020557d7bd</code></TableCell>
                    <TableCell><Chip label="Platform" size="small" color="primary" /></TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell>iCloud Keychain (Managed)</TableCell>
                    <TableCell><code style={{ fontSize: "0.75rem" }}>dd4ec289-e01d-41c9-bb89-70fa845d4bf2</code></TableCell>
                    <TableCell><Chip label="Platform" size="small" color="primary" /></TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell>Google Password Manager</TableCell>
                    <TableCell><code style={{ fontSize: "0.75rem" }}>ea9b8d66-4d01-1d21-3ce4-b6b48cb575d4</code></TableCell>
                    <TableCell><Chip label="Platform" size="small" color="primary" /></TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell>Chrome on Mac</TableCell>
                    <TableCell><code style={{ fontSize: "0.75rem" }}>adce0002-35bc-c60a-648b-0b25f1f05503</code></TableCell>
                    <TableCell><Chip label="Platform" size="small" color="primary" /></TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell>Windows Hello</TableCell>
                    <TableCell><code style={{ fontSize: "0.75rem" }}>08987058-cadc-4b81-b6e1-30de50dcbe96</code></TableCell>
                    <TableCell><Chip label="Platform" size="small" color="primary" /></TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell>1Password</TableCell>
                    <TableCell><code style={{ fontSize: "0.75rem" }}>bada5566-a7aa-401f-bd96-45619a55120d</code></TableCell>
                    <TableCell><Chip label="Manager" size="small" color="warning" /></TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell>Bitwarden</TableCell>
                    <TableCell><code style={{ fontSize: "0.75rem" }}>d548826e-79b4-db40-a3d8-11116f7e8349</code></TableCell>
                    <TableCell><Chip label="Manager" size="small" color="warning" /></TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell>Dashlane</TableCell>
                    <TableCell><code style={{ fontSize: "0.75rem" }}>531126d6-e717-415c-9320-3d9aa6981239</code></TableCell>
                    <TableCell><Chip label="Manager" size="small" color="warning" /></TableCell>
                  </TableRow>
                </TableBody>
              </Table>
            </TableContainer>
            <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: "block" }}>
              ※ 出典: <a href="https://github.com/passkeydeveloper/passkey-authenticator-aaguids" target="_blank" rel="noopener noreferrer">passkey-authenticator-aaguids</a> (コミュニティ管理)
            </Typography>
          </CardContent>
        </Card>

        {/* Attestation Preference Settings */}
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              <SecurityIcon sx={{ mr: 1, color: "info.main", verticalAlign: "middle" }} />
              attestation_preference 設定
            </Typography>
            <TableContainer component={Paper} variant="outlined">
              <Table size="small">
                <TableHead>
                  <TableRow sx={{ bgcolor: "grey.50" }}>
                    <TableCell><strong>値</strong></TableCell>
                    <TableCell><strong>説明</strong></TableCell>
                    <TableCell><strong>Platform認証器</strong></TableCell>
                    <TableCell><strong>Security Key</strong></TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  <TableRow>
                    <TableCell><code>none</code></TableCell>
                    <TableCell>アテステーションを要求しない</TableCell>
                    <TableCell>none を返す</TableCell>
                    <TableCell>none を返す</TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell><code>indirect</code></TableCell>
                    <TableCell>匿名化されたアテステーション</TableCell>
                    <TableCell>none を返す</TableCell>
                    <TableCell>匿名化される場合あり</TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell><code>direct</code></TableCell>
                    <TableCell>完全なアテステーションを要求</TableCell>
                    <TableCell>
                      <Chip label="none" size="small" color="warning" />
                      <Typography variant="caption" sx={{ ml: 0.5 }}>※仕様</Typography>
                    </TableCell>
                    <TableCell>
                      <Chip label="packed" size="small" color="success" />
                    </TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell><code>enterprise</code></TableCell>
                    <TableCell>エンタープライズ専用</TableCell>
                    <TableCell>none を返す</TableCell>
                    <TableCell>シリアル番号を含む場合あり</TableCell>
                  </TableRow>
                </TableBody>
              </Table>
            </TableContainer>
            <Alert severity="info" sx={{ mt: 2 }}>
              <strong>ポイント:</strong> Platform認証器（Touch ID等）は「direct」を要求しても「none」を返します。
              これはWebAuthn仕様に基づくプライバシー保護機能です。
            </Alert>
          </CardContent>
        </Card>

        <Divider />

        {/* Demo Section */}
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              <PlayArrowIcon sx={{ mr: 1, verticalAlign: "middle" }} />
              登録デモ実行
            </Typography>

            {/* Registration Options */}
            {currentStep === "start" && (
              <Paper variant="outlined" sx={{ p: 2, mb: 3, bgcolor: "grey.50" }}>
                <Typography variant="subtitle2" fontWeight="bold" gutterBottom>
                  登録オプションを選択
                </Typography>
                <Stack direction={{ xs: "column", md: "row" }} spacing={3} alignItems="flex-start">
                  <Box>
                    <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                      認証器タイプ
                    </Typography>
                    <ToggleButtonGroup
                      value={authenticatorType}
                      exclusive
                      onChange={(_, value) => value && setAuthenticatorType(value)}
                      size="small"
                    >
                      <ToggleButton value="platform">
                        <Tooltip title="Touch ID, Face ID, Windows Hello">
                          <Stack direction="row" alignItems="center" spacing={0.5}>
                            <PhoneIphoneIcon fontSize="small" />
                            <span>Platform</span>
                          </Stack>
                        </Tooltip>
                      </ToggleButton>
                      <ToggleButton value="cross-platform">
                        <Tooltip title="YubiKey, Titan Key等">
                          <Stack direction="row" alignItems="center" spacing={0.5}>
                            <UsbIcon fontSize="small" />
                            <span>Cross-platform</span>
                          </Stack>
                        </Tooltip>
                      </ToggleButton>
                    </ToggleButtonGroup>
                  </Box>
                  <Box>
                    <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                      アテステーション設定
                    </Typography>
                    <FormControl size="small" sx={{ minWidth: 150 }}>
                      <InputLabel>Attestation</InputLabel>
                      <Select
                        value={attestationPreference}
                        label="Attestation"
                        onChange={(e) => setAttestationPreference(e.target.value as AttestationPreference)}
                      >
                        <MenuItem value="none">none</MenuItem>
                        <MenuItem value="indirect">indirect</MenuItem>
                        <MenuItem value="direct">direct</MenuItem>
                        <MenuItem value="enterprise">enterprise</MenuItem>
                      </Select>
                    </FormControl>
                  </Box>
                </Stack>
                <Alert severity="info" sx={{ mt: 2 }} icon={<InfoIcon />}>
                  {authenticatorType === "platform" ? (
                    <>
                      <strong>Platform認証器を選択:</strong> Touch ID/Face IDが利用されます。
                      アテステーションは「{attestationPreference}」を要求しますが、「none」が返される可能性が高いです。
                    </>
                  ) : (
                    <>
                      <strong>Cross-platform認証器を選択:</strong> セキュリティキー（YubiKey等）が必要です。
                      「direct」を要求すると「packed」形式のアテステーションが返されます。
                    </>
                  )}
                </Alert>
              </Paper>
            )}

            {/* Progress Stepper */}
            <Stepper activeStep={getActiveStep()} sx={{ mb: 4 }}>
              {demoSteps.map((label) => (
                <Step key={label}>
                  <StepLabel>{label}</StepLabel>
                </Step>
              ))}
            </Stepper>

            <Stack spacing={3}>
              {/* Step 1: Start Authorization */}
              <Box>
                <Typography variant="subtitle2" fontWeight="bold" gutterBottom>
                  Step 1: 認可リクエストを開始
                </Typography>
                <Stack direction="row" spacing={2} alignItems="center">
                  <Button
                    variant="contained"
                    onClick={startAuthorization}
                    disabled={isRunning || currentStep !== "start"}
                    startIcon={isRunning ? <CircularProgress size={20} /> : <PlayArrowIcon />}
                  >
                    認可リクエスト開始
                  </Button>
                  {authorizationId && (
                    <Chip
                      label={`authorization_id: ${authorizationId.substring(0, 8)}...`}
                      color="success"
                      size="small"
                    />
                  )}
                  {currentStep === "start" && (
                    <Stack direction="row" spacing={1}>
                      <Chip
                        icon={authenticatorType === "platform" ? <PhoneIphoneIcon /> : <UsbIcon />}
                        label={authenticatorType}
                        size="small"
                        color={authenticatorType === "platform" ? "primary" : "secondary"}
                      />
                      <Chip
                        label={`attestation: ${attestationPreference}`}
                        size="small"
                        variant="outlined"
                      />
                    </Stack>
                  )}
                </Stack>
              </Box>

              {/* Step 2: User Registration */}
              {currentStep === "user-info" && (
                <Box>
                  <Typography variant="subtitle2" fontWeight="bold" gutterBottom>
                    Step 2: ユーザー情報を入力して登録
                  </Typography>
                  <Alert severity="info" sx={{ mb: 2 }}>
                    実際にメールを受信できるアドレスを入力してください。
                  </Alert>
                  <Stack spacing={2}>
                    <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
                      <TextField
                        label="メールアドレス"
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        size="small"
                        sx={{ width: 280 }}
                        required
                      />
                      <TextField
                        label="表示名"
                        value={username}
                        onChange={(e) => setUsername(e.target.value)}
                        size="small"
                        sx={{ width: 200 }}
                      />
                      <TextField
                        label="パスワード"
                        type="password"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        size="small"
                        sx={{ width: 200 }}
                        required
                      />
                    </Stack>
                    <Box>
                      <Button
                        variant="contained"
                        onClick={doInitialRegistration}
                        disabled={isRunning || !email || !password}
                        startIcon={isRunning ? <CircularProgress size={20} /> : <EmailIcon />}
                      >
                        登録してメール送信
                      </Button>
                    </Box>
                  </Stack>
                </Box>
              )}

              {/* Step 3-4: Email Verification */}
              {currentStep === "email-sent" && (
                <Box>
                  <Typography variant="subtitle2" fontWeight="bold" gutterBottom>
                    Step 3-4: メール認証コードを入力
                  </Typography>
                  <Alert severity="warning" sx={{ mb: 2 }}>
                    <AlertTitle>メールを確認してください</AlertTitle>
                    {email} に認証コードを送信しました。
                  </Alert>
                  <Stack direction="row" spacing={2} alignItems="center">
                    <TextField
                      label="認証コード"
                      value={verificationCode}
                      onChange={(e) => setVerificationCode(e.target.value)}
                      size="small"
                      sx={{ width: 200 }}
                      placeholder="123456"
                    />
                    <Button
                      variant="contained"
                      onClick={verifyEmail}
                      disabled={isRunning || !verificationCode}
                      startIcon={isRunning ? <CircularProgress size={20} /> : <CheckCircleIcon />}
                    >
                      認証コードを確認
                    </Button>
                  </Stack>
                </Box>
              )}

              {/* Step 5: Get FIDO2 Challenge */}
              {currentStep === "email-verified" && (
                <Box>
                  <Typography variant="subtitle2" fontWeight="bold" gutterBottom>
                    Step 5: FIDO2登録チャレンジを取得
                  </Typography>
                  <Alert severity="success" sx={{ mb: 2 }}>
                    メール認証が完了しました。FIDO2登録に進みます。
                  </Alert>
                  <Button
                    variant="contained"
                    onClick={getRegistrationChallenge}
                    disabled={isRunning}
                    startIcon={isRunning ? <CircularProgress size={20} /> : <FingerprintIcon />}
                  >
                    FIDO2チャレンジ取得
                  </Button>
                </Box>
              )}

              {/* Step 6: Execute Registration */}
              {currentStep === "fido2-ready" && challengeData && (
                <Box>
                  <Typography variant="subtitle2" fontWeight="bold" gutterBottom>
                    Step 6-8: FIDO2登録を実行
                  </Typography>
                  <Stack direction="row" spacing={1} sx={{ mb: 2 }}>
                    <Chip
                      icon={authenticatorType === "platform" ? <PhoneIphoneIcon /> : <UsbIcon />}
                      label={authenticatorType}
                      color={authenticatorType === "platform" ? "primary" : "secondary"}
                      size="small"
                    />
                    <Chip
                      label={`attestation: ${attestationPreference}`}
                      color="info"
                      size="small"
                    />
                  </Stack>
                  <Button
                    variant="contained"
                    color="success"
                    onClick={executeRegistration}
                    disabled={isRunning}
                    startIcon={isRunning ? <CircularProgress size={20} /> : <FingerprintIcon />}
                    size="large"
                  >
                    {authenticatorType === "platform" ? "Touch ID / Face ID で登録" : "セキュリティキーで登録"}
                  </Button>
                </Box>
              )}

              {/* Completed */}
              {currentStep === "completed" && (
                <Alert severity="success">
                  <AlertTitle>登録完了</AlertTitle>
                  FIDO2パスキーの登録が完了しました。登録履歴で結果を確認できます。
                </Alert>
              )}

              {/* Reset Button */}
              {testResults.length > 0 && (
                <Box>
                  <Button
                    variant="outlined"
                    onClick={resetTest}
                    startIcon={<RestartAltIcon />}
                  >
                    新しい登録を開始
                  </Button>
                </Box>
              )}
            </Stack>

            {/* Test Results */}
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

        {/* Registration History / Comparison */}
        {registrationHistory.length > 0 && (
          <Card>
            <CardContent>
              <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
                <Typography variant="h6">
                  <CompareArrowsIcon sx={{ mr: 1, verticalAlign: "middle" }} />
                  登録履歴・比較
                </Typography>
                <Button size="small" onClick={clearHistory} startIcon={<RestartAltIcon />}>
                  履歴をクリア
                </Button>
              </Stack>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                異なる認証器タイプやアテステーション設定で登録した結果を比較できます。
              </Typography>
              <TableContainer component={Paper} variant="outlined">
                <Table size="small">
                  <TableHead>
                    <TableRow sx={{ bgcolor: "grey.100" }}>
                      <TableCell><strong>#</strong></TableCell>
                      <TableCell><strong>認証器タイプ</strong></TableCell>
                      <TableCell><strong>attestation要求</strong></TableCell>
                      <TableCell><strong>返却されたformat</strong></TableCell>
                      <TableCell><strong>AAGUID</strong></TableCell>
                      <TableCell><strong>transports</strong></TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {registrationHistory.map((result, index) => (
                      <TableRow key={index}>
                        <TableCell>{registrationHistory.length - index}</TableCell>
                        <TableCell>
                          <Chip
                            icon={result.authenticatorType === "platform" ? <PhoneIphoneIcon /> : <UsbIcon />}
                            label={result.authenticatorType}
                            size="small"
                            color={result.authenticatorType === "platform" ? "primary" : "secondary"}
                          />
                        </TableCell>
                        <TableCell>
                          <code>{result.attestationPreference}</code>
                        </TableCell>
                        <TableCell>
                          <Chip
                            label={result.format}
                            size="small"
                            color={result.format === "none" ? "default" : "success"}
                          />
                        </TableCell>
                        <TableCell>
                          {lookupAaguid(result.aaguid) ? (
                            <Tooltip title={result.aaguid}>
                              <Chip
                                label={lookupAaguid(result.aaguid)}
                                size="small"
                                color="info"
                                variant="outlined"
                              />
                            </Tooltip>
                          ) : (
                            <code style={{ fontSize: "0.7rem" }}>
                              {result.aaguid.substring(0, 13) + "..."}
                            </code>
                          )}
                        </TableCell>
                        <TableCell>
                          {result.transports.length > 0 ? (
                            result.transports.map((t) => (
                              <Chip key={t} label={t} size="small" sx={{ mr: 0.5 }} />
                            ))
                          ) : (
                            <Typography variant="caption" color="text.secondary">-</Typography>
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
              {registrationHistory.length >= 2 && (
                <Alert severity="info" sx={{ mt: 2 }}>
                  <strong>比較ポイント:</strong>
                  <ul style={{ margin: "8px 0 0 0", paddingLeft: "20px" }}>
                    <li>Platform認証器は「direct」を要求しても「none」を返す（プライバシー保護）</li>
                    <li>Security Keyは「direct」で「packed」形式のアテステーションを返す</li>
                    <li>AAGUIDでパスキープロバイダーを識別可能（iCloud, Chrome, Windows Hello等）</li>
                    <li>transportsが認証器の接続方法を示す（internal=組込み, hybrid=QR, usb等）</li>
                  </ul>
                </Alert>
              )}
            </CardContent>
          </Card>
        )}

        {/* Back Link */}
        <Box>
          <Button href="/" variant="text">
            ← ホームに戻る
          </Button>
        </Box>
      </Stack>
    </Container>
  );
}
