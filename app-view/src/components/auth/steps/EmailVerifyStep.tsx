"use client";

import { useEffect, useRef, useState } from "react";
import {
  Box,
  Button,
  CircularProgress,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAtom } from "jotai";
import { backendUrl } from "@/pages/_app";
import { authIdentifierAtom } from "@/state/AuthState";
import { StepProps } from "./StepProps";

/**
 * Email verification step (method "email").
 *
 * Sends the challenge on mount (once) using the persisted identifier, then verifies the code. The
 * identifier comes from {@link authIdentifierAtom}, so this works on reload / direct access.
 */
export const EmailVerifyStep = ({ tenantId, id, onCompleted }: StepProps) => {
  const [identifier] = useAtom(authIdentifierAtom);
  const [code, setCode] = useState("");
  const [loading, setLoading] = useState(false);
  const [resending, setResending] = useState(false);
  const [message, setMessage] = useState("");
  const challengeSent = useRef(false);

  const sendChallenge = async () => {
    if (!identifier) return;
    setResending(true);
    try {
      await fetch(
        `${backendUrl}/${tenantId}/v1/authorizations/${id}/email-authentication-challenge`,
        {
          method: "POST",
          credentials: "include",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ email: identifier }),
        },
      );
    } finally {
      setResending(false);
    }
  };

  useEffect(() => {
    if (challengeSent.current) return;
    challengeSent.current = true;
    void sendChallenge();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleVerify = async () => {
    setLoading(true);
    setMessage("");
    try {
      const response = await fetch(
        `${backendUrl}/${tenantId}/v1/authorizations/${id}/email-authentication`,
        {
          method: "POST",
          credentials: "include",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ verification_code: code }),
        },
      );
      if (!response.ok) {
        setMessage("Invalid verification code. Please check and try again.");
        return;
      }
      await onCompleted();
    } finally {
      setLoading(false);
    }
  };

  return (
    <Stack spacing={3}>
      <Typography variant="body2" color="text.secondary">
        Enter the 6-digit code we sent to {identifier || "your email"}.
      </Typography>
      <TextField
        label="Verification code"
        value={code}
        onChange={(e) => setCode(e.target.value)}
        inputProps={{ inputMode: "numeric" }}
      />
      {message && (
        <Typography color="error" variant="body2">
          {message}
        </Typography>
      )}
      <Box display="flex" justifyContent="space-between" alignItems="center">
        <Button
          variant="text"
          disabled={resending}
          onClick={sendChallenge}
          sx={{ textTransform: "none" }}
        >
          {resending ? "Sending..." : "Resend code"}
        </Button>
        <Button
          variant="contained"
          disabled={loading || !code}
          onClick={handleVerify}
          sx={{ textTransform: "none" }}
        >
          {loading ? <CircularProgress size={24} /> : "Verify"}
        </Button>
      </Box>
    </Stack>
  );
};
