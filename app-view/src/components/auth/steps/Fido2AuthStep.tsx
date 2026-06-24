"use client";

import { useState } from "react";
import {
  Box,
  Button,
  CircularProgress,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import FingerprintIcon from "@mui/icons-material/Fingerprint";
import { useAtom } from "jotai";
import { backendUrl } from "@/pages/_app";
import { readErrorMessage } from "@/auth/http";
import { authIdentifierAtom } from "@/state/AuthState";
import { base64UrlToBuffer, bufferToBase64Url } from "@/auth/webauthn";
import { AuthAlert } from "@/components/auth/AuthAlert";
import { StepProps } from "./StepProps";

type Credential = {
  id: string;
  type: string;
  transports?: AuthenticatorTransport[];
};

type ChallengeResponse = {
  challenge: string;
  timeout?: number;
  rp_id?: string;
  rp?: { id?: string };
  allowCredentials?: Credential[];
  user_verification?: UserVerificationRequirement;
};

/**
 * Passkey authentication step (method "fido2", 2nd factor — `allow_registration: false`).
 *
 * Verifies an existing passkey via {@code navigator.credentials.get}. The username comes from the
 * persisted identifier so it survives reload. Use {@code Fido2Step} instead when the step registers
 * a new passkey.
 */
export const Fido2AuthStep = ({ tenantId, id, onCompleted }: StepProps) => {
  const [identifier] = useAtom(authIdentifierAtom);
  const [username, setUsername] = useState(identifier);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");

  const handleAuthenticate = async () => {
    setLoading(true);
    setMessage("");
    try {
      if (!window.PublicKeyCredential) {
        setMessage(
          "This browser doesn't support passkeys. Try a different browser or device.",
        );
        return;
      }

      const challengeRes = await fetch(
        `${backendUrl}/${tenantId}/v1/authorizations/${id}/fido2-authentication-challenge`,
        {
          method: "POST",
          credentials: "include",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            username,
            userVerification: "required",
            timeout: 60000,
          }),
        },
      );
      if (!challengeRes.ok) {
        setMessage(
          await readErrorMessage(
            challengeRes,
            "We couldn't start passkey sign-in. Please try again.",
          ),
        );
        return;
      }

      const {
        challenge,
        timeout = 60000,
        rp_id,
        rp,
        allowCredentials = [],
        user_verification = "required",
      }: ChallengeResponse = await challengeRes.json();

      const publicKeyOptions: PublicKeyCredentialRequestOptions = {
        challenge: base64UrlToBuffer(challenge),
        timeout,
        userVerification: user_verification,
      };
      const rpId = rp_id || rp?.id;
      if (rpId) publicKeyOptions.rpId = rpId;
      if (allowCredentials.length > 0) {
        publicKeyOptions.allowCredentials = allowCredentials.map((cred) => {
          const descriptor: PublicKeyCredentialDescriptor = {
            type: cred.type as PublicKeyCredentialType,
            id: base64UrlToBuffer(cred.id),
          };
          if (cred.transports?.length) descriptor.transports = cred.transports;
          return descriptor;
        });
      }

      const credential = (await navigator.credentials.get({
        publicKey: publicKeyOptions,
        mediation: "optional",
      })) as PublicKeyCredential | null;
      if (!credential) {
        setMessage("No passkey was used. Please try again.");
        return;
      }

      const assertionResponse =
        credential.response as AuthenticatorAssertionResponse;
      const credentialData = {
        id: credential.id,
        rawId: bufferToBase64Url(credential.rawId),
        type: credential.type,
        response: {
          clientDataJSON: bufferToBase64Url(assertionResponse.clientDataJSON),
          authenticatorData: bufferToBase64Url(
            assertionResponse.authenticatorData,
          ),
          signature: bufferToBase64Url(assertionResponse.signature),
          userHandle: assertionResponse.userHandle
            ? bufferToBase64Url(assertionResponse.userHandle)
            : null,
        },
        clientExtensionResults: credential.getClientExtensionResults(),
      };

      const authRes = await fetch(
        `${backendUrl}/${tenantId}/v1/authorizations/${id}/fido2-authentication`,
        {
          method: "POST",
          credentials: "include",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(credentialData),
        },
      );
      if (!authRes.ok) {
        setMessage(
          await readErrorMessage(
            authRes,
            "We couldn't verify your passkey. Please try again.",
          ),
        );
        return;
      }
      await onCompleted();
    } catch (error) {
      if (
        error instanceof Error &&
        (error.name === "NotAllowedError" || error.name === "AbortError")
      ) {
        setMessage("Passkey sign-in was cancelled. Please try again.");
      } else {
        setMessage("Something went wrong verifying your passkey. Please try again.");
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <Stack spacing={3}>
      <Typography variant="body2" color="text.secondary">
        Use your passkey to continue.
      </Typography>
      {!identifier && (
        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          autoFocus
          value={username}
          onChange={(e) => setUsername(e.target.value)}
        />
      )}
      <Box display="flex" justifyContent="center">
        <FingerprintIcon sx={{ fontSize: 40, color: "primary.main" }} />
      </Box>
      <AuthAlert message={message} />
      <Button
        variant="contained"
        disabled={loading}
        onClick={handleAuthenticate}
        fullWidth
        sx={{ textTransform: "none" }}
      >
        {loading ? <CircularProgress size={24} /> : "Use passkey"}
      </Button>
    </Stack>
  );
};
