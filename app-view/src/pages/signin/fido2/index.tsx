import { useEffect, useState } from "react";
import { Typography, Button, Stack, Link, Divider, Box, TextField } from "@mui/material";
import { useRouter } from "next/router";
import { backendUrl, useAppContext } from "@/pages/_app";
import { BaseLayout } from "@/components/layout/BaseLayout";
import { useQuery } from "@tanstack/react-query";
import { Loading } from "@/components/Loading";
import KeyIcon from "@mui/icons-material/Key";
import FingerprintIcon from "@mui/icons-material/Fingerprint";
import { SsoComponent } from "@/components/sso/SsoComponent";
import { useAtom } from "jotai";
import { authSessionIdAtom, authSessionTenantIdAtom } from "@/state/AuthState";

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
 * Required for serializing WebAuthn responses (Safari compatibility)
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

interface credential {
  id: string;
  type: string;
  transports?: AuthenticatorTransport[];
}

interface ChallengeResponse {
  challenge: string;
  timeout?: number;
  rp_id?: string;
  rp?: {
    id?: string;
    name?: string;
  };
  allowCredentials?: credential[];
  user_verification?: UserVerificationRequirement;
}

export default function Login() {
  const [, setAuthSessionId] = useAtom(authSessionIdAtom);
  const [, setAuthSessionTenantId] = useAtom(authSessionTenantIdAtom);

  const router = useRouter();
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");
  const { email: contextEmail } = useAppContext();
  const { id, tenant_id: tenantId } = router.query;

  // Username/email input for conditional UI (autofill)
  const [username, setUsername] = useState(contextEmail || "");

  // AbortController for managing concurrent authentication requests
  const [currentCredentialGetController, setCurrentCredentialGetController] = useState<AbortController | null>(null);
  // Flag to prevent duplicate challenge requests
  const [isFetching, setIsFetching] = useState(false);

  const { data, isPending } = useQuery({
    queryKey: ["fetchViewData", router.query],
    queryFn: async () => {
      if (!router.isReady || Object.keys(router.query).length === 0) return; // Ensure query params exist

      const { id, tenant_id: tenantId } = router.query;
      if (typeof id === "string") {
        setAuthSessionId(id);
      }
      if (typeof tenantId === "string") {
        setAuthSessionTenantId(tenantId);
      }

      const response = await fetch(
        `${backendUrl}/${tenantId}/v1/authorizations/${id}/view-data`,
        {
          credentials: "include",
        },
      );
      if (!response.ok) {
        console.error(response);
        throw new Error(response.status.toString());
      }
      return await response.json();
    },
  });

  /**
   * Handle manual button click - trigger authentication in manual mode
   */
  const handleNext = async () => {
    console.log("handleNext called");
    setLoading(true);
    try {
      console.log("Calling authChallenge with manual mode");
      await authChallenge(false); // false = manual button mode
    } catch (error) {
      console.error("Error in handleNext:", error);
    } finally {
      setLoading(false);
    }
  };

  /**
   * Request authentication challenge from server and initiate WebAuthn authentication
   * @param isConditional - true for conditional UI (autofill), false for manual button
   */
  const authChallenge = async (isConditional: boolean) => {
    console.log("authChallenge called, isConditional:", isConditional);

    // Check browser compatibility
    if (!window.PublicKeyCredential) {
      setMessage("Your browser does not support passkey authentication. Please use a modern browser.");
      return;
    }

    // Prevent duplicate requests for manual mode only
    // Conditional UI can run in background while manual mode is triggered
    if (!isConditional && isFetching) {
      console.log("Already fetching in manual mode, skipping");
      return;
    }

    // Only set isFetching for manual mode
    if (!isConditional) {
      console.log("Setting isFetching to true for manual mode");
      setIsFetching(true);
    }

    // Clear error messages for manual mode
    if (!isConditional) {
      setMessage("");
    }

    try {
      // Step 1: Request challenge from server
      const res = await fetch(
        `${backendUrl}/${tenantId}/v1/authorizations/${id}/fido2-authentication-challenge`,
        {
          method: "POST",
          credentials: "include",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            username: username || contextEmail,
            userVerification: "required",
            timeout: 60000
          })
        },
      );

      if (!res.ok) {
        // Handle server errors
        let errMsg;
        switch (res.status) {
          case 401:
            errMsg = 'Session expired. Please log in again.';
            break;
          case 429:
            errMsg = 'Passkey authentication is temporarily unavailable due to rate limiting. Please try again later or use password login.';
            break;
          case 503:
            errMsg = 'Passkey authentication service is currently unavailable. Please try again later or use password login.';
            break;
          default:
            errMsg = isConditional
              ? 'Passkey authentication is currently unavailable. Please try again later or use password login.'
              : 'System error. Please try again.';
            break;
        }
        setMessage(errMsg);
        return;
      }

      const challengeResponse: ChallengeResponse = await res.json();
      const {
        challenge,
        timeout = 60000,
        rp_id,
        rp,
        allowCredentials = [],
        user_verification = "required"
      } = challengeResponse;

      // Extract rpId from either flat rp_id or nested rp.id
      const rpId = rp_id || rp?.id;

      // Step 2: Build PublicKeyCredentialRequestOptions
      const publicKeyOptions: PublicKeyCredentialRequestOptions = {
        challenge: base64UrlToBuffer(challenge),
        timeout,
        userVerification: user_verification,
      };

      // Add rpId if provided by server (required for subdomain deployments)
      if (rpId) {
        publicKeyOptions.rpId = rpId;
      }

      // Add allowCredentials if provided by server
      if (allowCredentials.length > 0) {
        publicKeyOptions.allowCredentials = allowCredentials.map((cred) => {
          const descriptor: PublicKeyCredentialDescriptor = {
            type: cred.type as PublicKeyCredentialType,
            id: base64UrlToBuffer(cred.id),
          };
          // Include transports if available - this helps the browser
          // identify the correct authenticator (e.g., "internal" for Touch ID)
          if (cred.transports && cred.transports.length > 0) {
            descriptor.transports = cred.transports;
          }
          return descriptor;
        });
      }

      // Step 3: Initiate authentication
      await auth(publicKeyOptions, isConditional);
    } catch (error) {
      console.error(error);
      const errMsg = isConditional
        ? 'Passkey authentication is currently unavailable. Please try again later or use password login.'
        : 'Failed to get authentication challenge. Please try again.';
      setMessage(errMsg);
    } finally {
      // Only reset isFetching for manual mode
      if (!isConditional) {
        console.log("Resetting isFetching to false");
        setIsFetching(false);
      }
    }
  };

  /**
   * Perform WebAuthn authentication
   * @param authOptions - PublicKeyCredentialRequestOptions
   * @param isConditional - Whether this is conditional UI (autofill) or manual button
   */
  const auth = async (authOptions: PublicKeyCredentialRequestOptions, isConditional: boolean) => {
    try {
      // Abort existing credential.get() request if any
      if (currentCredentialGetController) {
        currentCredentialGetController.abort();
      }

      // Create new AbortController for this request
      const controller = new AbortController();
      setCurrentCredentialGetController(controller);

      const credential = await navigator.credentials.get({
        publicKey: authOptions,
        // conditional: autofill UI, optional: manual button
        mediation: isConditional ? 'conditional' : 'optional',
        signal: controller.signal,
      }) as PublicKeyCredential;

      // Submit credential to server
      await authSubmit(credential);
    } catch (error) {
      if (!isConditional) {
        // Manual button mode: show error to user
        if (error instanceof Error) {
          if (error.name === 'AbortError' || error.name === 'NotAllowedError') {
            setMessage('Authentication was cancelled. Please ensure the selected passkey is available and try again.');
          } else {
            setMessage('An error occurred during authentication. Please ensure the selected passkey is available and try again.');
          }
        }
        // Restart conditional UI after manual error
        // Use setTimeout to ensure isFetching flag is reset first
        setTimeout(() => {
          authChallenge(true);
        }, 100);
      } else {
        // Conditional mode: only show error if it's not AbortError
        // (AbortError is expected when manual button is clicked)
        if (error instanceof Error && error.name !== 'AbortError') {
          setMessage('An error occurred during authentication. Please ensure the selected passkey is available and try again.');
        }
      }
    } finally {
      setCurrentCredentialGetController(null);
    }
  };

  /**
   * Submit authenticated credential to server and handle authorization
   */
  const authSubmit = async (credential: PublicKeyCredential) => {
    if (!credential) {
      throw new Error("No credential received from authenticator");
    }

    // Serialize credential for transmission
    // PublicKeyCredential contains ArrayBuffer fields that don't serialize with JSON.stringify
    // Safari especially requires manual serialization
    const assertionResponse = credential.response as AuthenticatorAssertionResponse;
    const credentialData = {
      id: credential.id,
      rawId: bufferToBase64Url(credential.rawId),
      type: credential.type,
      response: {
        clientDataJSON: bufferToBase64Url(assertionResponse.clientDataJSON),
        authenticatorData: bufferToBase64Url(assertionResponse.authenticatorData),
        signature: bufferToBase64Url(assertionResponse.signature),
        userHandle: assertionResponse.userHandle ? bufferToBase64Url(assertionResponse.userHandle) : null,
      },
      clientExtensionResults: credential.getClientExtensionResults(),
    };

    console.log("Serialized credential data:", credentialData);

    // Submit credential to server for verification
    const loginRes = await fetch(
      `${backendUrl}/${tenantId}/v1/authorizations/${id}/fido2-authentication`,
      {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(credentialData),
      },
    );

    if (loginRes.ok) {
      // Proceed with authorization
      const authorizeResponse = await fetch(
        `${backendUrl}/${tenantId}/v1/authorizations/${id}/authorize`,
        {
          method: "POST",
          credentials: "include",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            action: "signin",
          }),
        },
      );
      const body = await authorizeResponse.json();
      console.log(authorizeResponse.status, body);
      if (body.redirect_uri) {
        window.location.href = body.redirect_uri;
      }
      return;
    }

    throw new Error("Login failed");
  };

  const handleCancel = async () => {
    const response = await fetch(
      `${backendUrl}/${tenantId}/v1/authorizations/${id}/deny`,
      {
        method: "POST",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
        },
      },
    );
    const body = await response.json();
    console.log(response.status, body);
    if (body.redirect_uri) {
      window.location.href = body.redirect_uri;
    }
  };

  useEffect(() => {
    const execute = async () => {
      const response = await fetch(
        `${backendUrl}/${tenantId}/v1/authorizations/${id}/authorize-with-session`,
        {
          method: "POST",
          credentials: "include",
          headers: {
            "Content-Type": "application/json",
          },
        },
      );
      if (!response.ok) {
        console.error(response);
        return;
      }
      const body = await response.json();
      console.log(response.status, body);
      if (body.redirect_uri) {
        window.location.href = body.redirect_uri;
      }
    };
    console.log(data);
    if (data && data.session_enabled === true) {
      execute();
    }
  }, [data, id, tenantId]);

  /**
   * Update username when context email changes
   */
  useEffect(() => {
    if (contextEmail) {
      setUsername(contextEmail);
    }
  }, [contextEmail]);

  /**
   * Start conditional UI (autofill) on page load
   * This enables passkey selection from username input field (iOS Safari, etc.)
   */
  useEffect(() => {
    if (data && !data.session_enabled && router.isReady && tenantId && id) {
      // Start conditional UI mode
      authChallenge(true);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data, router.isReady, tenantId, id]);

  if (isPending) return <Loading />;
  if (!data) return <Loading />;
  if (data && data.session_enabled) return <Loading />;

  return (
    <BaseLayout>
      <Stack spacing={3} alignItems="center">
        <Typography variant="subtitle1" fontWeight="medium">
          IdP Server
        </Typography>
        <Stack spacing={3} width="100%">
          <Box display={"flex"} justifyContent={"center"} sx={{ gap: 2 }}>
            <KeyIcon />
            <FingerprintIcon />
          </Box>

          <Box>
            <Typography variant={"body1"}>
              Sign in quickly and securely using Passkey
            </Typography>
            <Typography variant={"body1"}>—no password required</Typography>
          </Box>

          <Stack spacing={2} width="100%">
            <TextField
              fullWidth
              label="Username or Email"
              placeholder="Enter your username or email"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoComplete="username webauthn"
              inputProps={{
                autoComplete: "username webauthn",
              }}
              sx={{
                "& .MuiOutlinedInput-root": {
                  borderRadius: 2,
                },
              }}
            />
          </Stack>

          <Stack spacing={1} width="100%">
            {message && (
              <Typography
                color="error"
                variant="body2"
                sx={{
                  mt: 1,
                  p: 1.5,
                  backgroundColor: "#FEF2F2",
                  borderRadius: 1,
                  border: "1px solid #FCA5A5"
                }}
              >
                {message}
              </Typography>
            )}

            <Button
              variant="contained"
              disabled={loading}
              onClick={handleNext}
              fullWidth
              sx={{
                textTransform: "none",
                borderRadius: 8,
                height: 44,
                fontSize: 16,
                fontWeight: "bold",
                backgroundColor: "#007AFF",
                boxShadow: "0px 2px 5px rgba(0, 0, 0, 0.1)",
                "&:hover": { opacity: 0.8 },
              }}
            >
              Signin With Passkey
            </Button>
            {data.show_cancel && (
              <Button
                variant="outlined"
                onClick={handleCancel}
                sx={{
                  textTransform: "none",
                  fontSize: 16,
                  fontWeight: "medium",
                  color: "#505050",
                  borderColor: "rgba(0, 0, 0, 0.2)",
                }}
              >
                Cancel
              </Button>
            )}
          </Stack>
          <Stack
            spacing={1}
            direction="row"
            justifyContent="center"
            sx={{ mt: 3 }}
          >
            <Typography variant="body2">{"Don't have an account?"}</Typography>
            <Link
              onClick={() =>
                router.push(`/signup?id=${id}&tenant_id=${tenantId}`)
              }
              sx={{
                fontWeight: "bold",
                cursor: "pointer",
                color: "primary.main",
                fontSize: 16,
              }}
            >
              Sign Up
            </Link>
          </Stack>
        </Stack>

        <Stack spacing={2} sx={{ width: "100%" }}>
          <Divider variant={"fullWidth"} />
          <SsoComponent />
        </Stack>

        <Typography variant="caption" color="text.secondary" sx={{ mt: 2 }}>
          By signing in, you agree to our
          <Link href={data.tos_uri} sx={{ fontWeight: "bold", mx: 0.5 }}>
            Terms of Use
          </Link>
          and
          <Link href={data.policy_uri} sx={{ fontWeight: "bold", mx: 0.5 }}>
            Privacy Policy
          </Link>
        </Typography>
      </Stack>
    </BaseLayout>
  );
}
