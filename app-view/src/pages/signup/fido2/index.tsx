"use client";

import { useState, useEffect, useRef } from "react";
import {
  Container,
  Typography,
  Button,
  Box,
  CircularProgress,
  Paper,
  Stack,
  useTheme,
  alpha,
} from "@mui/material";
import KeyIcon from "@mui/icons-material/Key";
import { useRouter } from "next/router";
import { backendUrl, useAppContext } from "@/pages/_app";
import { SignupStepper } from "@/components/SignupStepper";
import { AddPasskeyStepper } from "@/components/AddPasskeyStepper";

/**
 * Convert Base64URL string to ArrayBuffer
 * WebAuthn uses Base64URL encoding for binary data transmission
 */
const base64UrlToBuffer = (base64url: string): Uint8Array => {
  const base64 = base64url.replace(/-/g, '+').replace(/_/g, '/');
  const binaryString = atob(base64);
  return Uint8Array.from(binaryString, (char) => char.charCodeAt(0));
};

/**
 * Convert ArrayBuffer to Base64URL string
 * Required for serializing WebAuthn responses
 */
const bufferToBase64Url = (buffer: ArrayBuffer): string => {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  const base64 = btoa(binary);
  return base64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
};

interface ChallengeResponse {
  challenge: string;
  rp?: {
    id?: string;
    name: string;
  };
  user: {
    id: string;
    name: string;
    displayName: string;
  };
  pubKeyCredParams?: PublicKeyCredentialParameters[];
  timeout?: number;
  authenticatorSelection?: AuthenticatorSelectionCriteria;
  attestation?: AttestationConveyancePreference;
  extensions?: AuthenticationExtensionsClientInputs;
}

export default function Fido2RegistrationPage() {
  const router = useRouter();
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");
  const [isRegistering, setIsRegistering] = useState(false);
  const { email } = useAppContext();
  const { id, tenant_id: tenantId, flow } = router.query;
  const theme = useTheme();
  const isAddPasskeyFlow = flow === "add-passkey";
  const abortControllerRef = useRef<AbortController | null>(null);

  // Cleanup any pending WebAuthn operations on mount and unmount
  useEffect(() => {
    // Create an AbortController on mount to handle any lingering operations
    abortControllerRef.current = new AbortController();

    return () => {
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }
    };
  }, []);

  const handleRegister = async () => {
    // Prevent duplicate requests
    if (isRegistering) {
      console.warn("Registration already in progress");
      return;
    }

    // Abort any previous operation and wait for cleanup
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      // Wait for browser to clean up the aborted operation
      await new Promise(resolve => setTimeout(resolve, 100));
    }
    abortControllerRef.current = new AbortController();

    setLoading(true);
    setIsRegistering(true);
    setMessage("");

    try {
      // Check browser compatibility
      if (!window.PublicKeyCredential) {
        setMessage("Your browser does not support passkey registration. Please use a modern browser.");
        return;
      }

      // Cancel any active conditional mediation (passkey autofill) from other pages
      // This is necessary because Chrome keeps conditional mediation active across page navigations
      try {
        await navigator.credentials.preventSilentAccess();
        // Give browser time to clean up
        await new Promise(resolve => setTimeout(resolve, 100));
      } catch (e) {
        console.log("preventSilentAccess not supported or failed:", e);
      }

      // Check if platform authenticator is available
      const available = await PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable();
      console.log("Platform authenticator available:", available);

      if (!available) {
        console.warn("Platform authenticator (Touch ID) is not available on this device");
      }

      // Step 1: Request challenge from server
      const res = await fetch(
        `${backendUrl}/${tenantId}/v1/authorizations/${id}/fido2-registration-challenge`,
        {
          method: "POST",
          credentials: "include",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            username: email,
            displayName: email,
            authenticatorSelection: {
              requireResidentKey: true,
              userVerification: "required"
            },
            attestation: "direct",
            extensions: {
              credProps: true
            }
          })
        },
      );

      if (!res.ok) {
        throw new Error(`Failed to get challenge: ${res.status}`);
      }

      const challengeResponse: ChallengeResponse = await res.json();

      // Debug: Log full server response
      console.log("Full server response:", challengeResponse);

      const {
        challenge,
        rp = { name: "IdP Server" },
        user,
        pubKeyCredParams,
        timeout = 60000,
        authenticatorSelection,
        attestation = "direct",
        extensions
      } = challengeResponse;

      // Debug: Log what server sent
      console.log("Server authenticatorSelection:", authenticatorSelection);

      // Step 2: Build PublicKeyCredentialCreationOptions
      const publicKeyOptions: PublicKeyCredentialCreationOptions = {
        challenge: base64UrlToBuffer(challenge),
        rp: rp,
        user: {
          id: base64UrlToBuffer(user.id),
          name: user.name,
          displayName: user.displayName,
        },
        // Allow both platform (Touch ID, Face ID, Windows Hello) and
        // cross-platform (YubiKey, Titan Key) authenticators
        // by not specifying authenticatorAttachment
        authenticatorSelection: {
          requireResidentKey: true,
          userVerification: "required"
        },
        // Include ES256 (-7) and RS256 (-257) for cross-platform compatibility
        pubKeyCredParams: pubKeyCredParams || [
          { type: "public-key", alg: -7 },   // ES256 (default)
          { type: "public-key", alg: -257 }, // RS256 (fallback)
        ] as PublicKeyCredentialParameters[],
        timeout,
        attestation,
      };

      // Add extensions if provided
      if (extensions) {
        publicKeyOptions.extensions = extensions;
      }

      // NOTE: Using client-side authenticatorSelection to allow all authenticator types

      // Debug: Log final options being sent to WebAuthn API
      console.log("Final publicKeyOptions:", publicKeyOptions);

      // Step 3: Create credential with retry for "pending" error
      let credential: PublicKeyCredential | null = null;
      let retryCount = 0;
      const maxRetries = 3;

      while (!credential && retryCount < maxRetries) {
        try {
          credential = await navigator.credentials.create({
            publicKey: publicKeyOptions,
            signal: abortControllerRef.current?.signal,
          }) as PublicKeyCredential;
        } catch (createError) {
          if (createError instanceof Error &&
              createError.name === 'OperationError' &&
              createError.message.includes('pending') &&
              retryCount < maxRetries - 1) {
            console.log(`WebAuthn pending error, retrying... (${retryCount + 1}/${maxRetries})`);
            retryCount++;
            // Wait before retry
            await new Promise(resolve => setTimeout(resolve, 500));
          } else {
            throw createError;
          }
        }
      }

      if (!credential) {
        throw new Error("No credential received from authenticator");
      }

      // Step 4: Serialize credential for transmission
      // PublicKeyCredential contains ArrayBuffer fields that don't serialize with JSON.stringify
      // Safari especially requires manual serialization
      const attestationResponse = credential.response as AuthenticatorAttestationResponse;
      const credentialData = {
        id: credential.id,
        rawId: bufferToBase64Url(credential.rawId),
        type: credential.type,
        response: {
          clientDataJSON: bufferToBase64Url(attestationResponse.clientDataJSON),
          attestationObject: bufferToBase64Url(attestationResponse.attestationObject),
          // Include transports if available (for better UX on future authentications)
          transports: attestationResponse.getTransports ? attestationResponse.getTransports() : [],
        },
        // Include client extension results if available
        clientExtensionResults: credential.getClientExtensionResults(),
      };

      console.log("Serialized credential data:", credentialData);

      // Step 5: Submit credential to server
      const registerRes = await fetch(
        `${backendUrl}/${tenantId}/v1/authorizations/${id}/fido2-registration`,
        {
          method: "POST",
          credentials: "include",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(credentialData),
        },
      );

      if (registerRes.ok) {
        setMessage("Passkey registration successful!");
        // Redirect based on flow
        if (isAddPasskeyFlow) {
          // For add-passkey flow, authorize and redirect back to client
          router.push(`/signup/authorize?id=${id}&tenant_id=${tenantId}&flow=add-passkey`);
        } else {
          // For signup flow, continue to authorization
          router.push(`/signup/authorize?id=${id}&tenant_id=${tenantId}`);
        }
        return;
      }

      const errorData = await registerRes.json().catch(() => ({}));
      setMessage(errorData.error || "Registration failed.");
    } catch (error) {
      console.error(error);

      // Handle WebAuthn-specific errors
      if (error instanceof Error) {
        if (error.name === 'AbortError') {
          // Don't show error if abort was caused by component cleanup or user navigation
          console.log("WebAuthn operation was aborted");
          return;
        } else if (error.name === 'NotAllowedError') {
          setMessage("Registration was not allowed. Please ensure you have permission and try again.");
        } else if (error.name === 'InvalidStateError') {
          setMessage("This authenticator is already registered. Please use a different one or sign in.");
        } else if (error.name === 'NotSupportedError') {
          setMessage("This browser does not support passkey registration.");
        } else if (error.message.includes('Failed to get challenge')) {
          setMessage("Failed to get registration challenge from server. Please try again.");
        } else {
          setMessage(`An error occurred: ${error.message}`);
        }
      } else {
        setMessage("An unexpected error occurred during registration.");
      }
    } finally {
      setLoading(false);
      setIsRegistering(false);
    }
  };

  return (
    <Container maxWidth="xs">
      <Paper
        elevation={0}
        sx={{
          borderRadius: 4,
          px: 5,
          py: 6,
          mt: 8,
          backgroundColor:
            theme.palette.mode === "light"
              ? "#fcfcfd"
              : alpha(theme.palette.common.white, 0.035),
          border: `1px solid ${alpha(theme.palette.divider, 0.08)}`,
          boxShadow:
            theme.palette.mode === "light"
              ? "0 6px 24px rgba(0,0,0,0.025)"
              : "0 0 0 1px rgba(255,255,255,0.06)",
        }}
      >
        <Typography variant="h5" fontWeight={600} gutterBottom>
          {isAddPasskeyFlow ? "Add New Passkey" : "Passkey Registration"}
        </Typography>
        <Typography variant="body2" color="text.secondary" mb={4}>
          {isAddPasskeyFlow
            ? "Register an additional passkey for backup or use on another device."
            : "Secure your account with a passkey for fast and passwordless sign-in."}
        </Typography>

        <Stack spacing={3}>
          {isAddPasskeyFlow ? (
            <AddPasskeyStepper activeStep={2} />
          ) : (
            <SignupStepper activeStep={2} />
          )}

          <Box display="flex" justifyContent="center">
            <KeyIcon sx={{ fontSize: 40, color: "primary.main" }} />
          </Box>

          <Box>
            <Button
              variant="contained"
              color="primary"
              onClick={handleRegister}
              disabled={loading}
              fullWidth
              sx={{ textTransform: "none" }}
            >
              {loading ? <CircularProgress size={24} /> : "Register Passkey"}
            </Button>
          </Box>

          {message && (
            <Typography
              color={message.includes("successful") ? "success.main" : "error"}
              variant="body2"
              align="center"
              sx={{
                mt: 1,
                p: 1.5,
                backgroundColor: message.includes("successful") ? "#F0FDF4" : "#FEF2F2",
                borderRadius: 1,
                border: message.includes("successful") ? "1px solid #86EFAC" : "1px solid #FCA5A5"
              }}
            >
              {message}
            </Typography>
          )}
        </Stack>
      </Paper>
    </Container>
  );
}
