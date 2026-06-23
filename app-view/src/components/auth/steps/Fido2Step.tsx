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
import KeyIcon from "@mui/icons-material/Key";
import { useAtom } from "jotai";
import { backendUrl } from "@/pages/_app";
import { authIdentifierAtom } from "@/state/AuthState";
import { base64UrlToBuffer, bufferToBase64Url } from "@/auth/webauthn";
import { StepProps } from "./StepProps";

type ChallengeResponse = {
  challenge: string;
  rp?: { id?: string; name: string };
  user: { id: string; name: string; displayName: string };
  pubKeyCredParams?: PublicKeyCredentialParameters[];
  timeout?: number;
  attestation?: AttestationConveyancePreference;
  extensions?: AuthenticationExtensionsClientInputs;
};

/**
 * Passkey registration step (method "fido2").
 *
 * The username is the persisted identifier ({@link authIdentifierAtom}), which survives reload and
 * direct access — this is the fix for the original bug where the username came from the in-memory
 * AppContext and was sent empty after a reload, causing a `forbidden` error (Issue #1373).
 */
export const Fido2Step = ({ tenantId, id, onCompleted }: StepProps) => {
  const [identifier, setIdentifier] = useAtom(authIdentifierAtom);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");

  const handleRegister = async () => {
    if (!identifier) {
      setMessage("Enter your email to continue.");
      return;
    }
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
        `${backendUrl}/${tenantId}/v1/authorizations/${id}/fido2-registration-challenge`,
        {
          method: "POST",
          credentials: "include",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            username: identifier,
            displayName: identifier,
            authenticatorSelection: {
              requireResidentKey: true,
              userVerification: "required",
            },
            attestation: "direct",
            extensions: { credProps: true },
          }),
        },
      );
      if (!challengeRes.ok) {
        setMessage("We couldn't start passkey setup. Please try again.");
        return;
      }

      const {
        challenge,
        rp = { name: "IdP Server" },
        user,
        pubKeyCredParams,
        timeout = 60000,
        attestation = "direct",
        extensions,
      }: ChallengeResponse = await challengeRes.json();

      const publicKeyOptions: PublicKeyCredentialCreationOptions = {
        challenge: base64UrlToBuffer(challenge),
        rp,
        user: {
          id: base64UrlToBuffer(user.id),
          name: user.name,
          displayName: user.displayName,
        },
        authenticatorSelection: {
          requireResidentKey: true,
          userVerification: "required",
        },
        pubKeyCredParams: pubKeyCredParams || [
          { type: "public-key", alg: -7 },
          { type: "public-key", alg: -257 },
        ],
        timeout,
        attestation,
      };
      if (extensions) publicKeyOptions.extensions = extensions;

      const credential = (await navigator.credentials.create({
        publicKey: publicKeyOptions,
      })) as PublicKeyCredential | null;
      if (!credential) {
        setMessage("No passkey was created. Please try again.");
        return;
      }

      const attestationResponse =
        credential.response as AuthenticatorAttestationResponse;
      const credentialData = {
        id: credential.id,
        rawId: bufferToBase64Url(credential.rawId),
        type: credential.type,
        response: {
          clientDataJSON: bufferToBase64Url(attestationResponse.clientDataJSON),
          attestationObject: bufferToBase64Url(
            attestationResponse.attestationObject,
          ),
          transports: attestationResponse.getTransports
            ? attestationResponse.getTransports()
            : [],
        },
        clientExtensionResults: credential.getClientExtensionResults(),
      };

      const registerRes = await fetch(
        `${backendUrl}/${tenantId}/v1/authorizations/${id}/fido2-registration`,
        {
          method: "POST",
          credentials: "include",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(credentialData),
        },
      );
      if (!registerRes.ok) {
        setMessage("We couldn't register your passkey. Please try again.");
        return;
      }
      await onCompleted();
    } catch (error) {
      if (error instanceof Error && error.name === "NotAllowedError") {
        setMessage("Passkey setup was cancelled or not allowed. Please try again.");
      } else {
        setMessage("Something went wrong setting up your passkey. Please try again.");
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <Stack spacing={3}>
      <Typography variant="body2" color="text.secondary">
        Set up a passkey to sign in securely next time — no password needed.
      </Typography>
      {!identifier && (
        <TextField
          label="Email"
          value={identifier}
          onChange={(e) => setIdentifier(e.target.value)}
        />
      )}
      <Box display="flex" justifyContent="center">
        <KeyIcon sx={{ fontSize: 40, color: "primary.main" }} />
      </Box>
      {message && (
        <Typography color="error" variant="body2" align="center">
          {message}
        </Typography>
      )}
      <Button
        variant="contained"
        disabled={loading}
        onClick={handleRegister}
        fullWidth
        sx={{ textTransform: "none" }}
      >
        {loading ? <CircularProgress size={24} /> : "Set up passkey"}
      </Button>
    </Stack>
  );
};
