"use client";

import { useEffect, useRef, useState } from "react";
import {
  Button,
  CircularProgress,
  InputAdornment,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { Email } from "@mui/icons-material";
import { useAtom } from "jotai";
import { backendUrl } from "@/pages/_app";
import { readErrorMessage, readUserStatus } from "@/auth/http";
import { needsContactInput } from "@/auth/stepHelpers";
import { authIdentifierAtom, authUserStatusAtom } from "@/state/AuthState";
import { AuthAlert } from "@/components/auth/AuthAlert";
import { OtpInput } from "@/components/auth/OtpInput";
import { ResendLink } from "@/components/auth/ResendLink";
import { StepProps } from "./StepProps";

const CODE_LENGTH = 6;

/**
 * Email verification step (method "email").
 *
 * Two phases: a 1st-factor step (`requires_user: false`) first asks for the email address, then
 * sends the code; a 2nd-factor step (the user is already identified) sends the code on mount. The
 * captured email is persisted to {@link authIdentifierAtom} so it survives reload / direct access.
 */
export const EmailVerifyStep = ({
  tenantId,
  id,
  step,
  onCompleted,
}: StepProps) => {
  const [identifier, setIdentifier] = useAtom(authIdentifierAtom);
  const [userStatus, setUserStatus] = useAtom(authUserStatusAtom);
  const [email, setEmail] = useState(identifier);
  const [code, setCode] = useState("");
  const [phase, setPhase] = useState<"identify" | "verify">(
    needsContactInput(step, userStatus) ? "identify" : "verify",
  );
  const [loading, setLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const [message, setMessage] = useState("");
  const autoSent = useRef(false);

  const sendChallenge = async (target: string): Promise<boolean> => {
    setSending(true);
    setMessage("");
    try {
      const response = await fetch(
        `${backendUrl}/${tenantId}/v1/authorizations/${id}/email-authentication-challenge`,
        {
          method: "POST",
          credentials: "include",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ email: target }),
        },
      );
      if (!response.ok) {
        setMessage(
          await readErrorMessage(
            response,
            "We couldn't send the code. Please try again.",
          ),
        );
        return false;
      }
      return true;
    } finally {
      setSending(false);
    }
  };

  // 2nd-factor step: the user is already identified, so send the code once on mount.
  useEffect(() => {
    if (phase !== "verify" || autoSent.current || !identifier) return;
    autoSent.current = true;
    void sendChallenge(identifier);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleSendCode = async () => {
    if (!email.includes("@")) {
      setMessage("Enter a valid email address.");
      return;
    }
    if (!(await sendChallenge(email))) return;
    setIdentifier(email);
    setPhase("verify");
  };

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
        setMessage(
          await readErrorMessage(
            response,
            "That code is incorrect or has expired. Please try again.",
          ),
        );
        return;
      }
      setUserStatus(await readUserStatus(response));
      await onCompleted();
    } finally {
      setLoading(false);
    }
  };

  if (phase === "identify") {
    return (
      <Stack spacing={3}>
        <Typography variant="body2" color="text.secondary">
          Enter your email and we&apos;ll send you a verification code.
        </Typography>
        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          autoFocus
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <Email />
              </InputAdornment>
            ),
          }}
        />
        <AuthAlert message={message} />
        <Button
          variant="contained"
          fullWidth
          disabled={sending || !email}
          onClick={handleSendCode}
          sx={{ textTransform: "none" }}
        >
          {sending ? <CircularProgress size={24} /> : "Send code"}
        </Button>
      </Stack>
    );
  }

  return (
    <Stack spacing={3}>
      <Typography variant="body2" color="text.secondary">
        We sent a 6-digit code to {identifier || "your email"}. Enter it below to
        continue.
      </Typography>
      <OtpInput value={code} onChange={setCode} length={CODE_LENGTH} autoFocus />
      <AuthAlert message={message} />
      <Button
        variant="contained"
        fullWidth
        disabled={loading || code.length < CODE_LENGTH}
        onClick={handleVerify}
        sx={{ textTransform: "none" }}
      >
        {loading ? <CircularProgress size={24} /> : "Verify"}
      </Button>
      <ResendLink onResend={() => sendChallenge(identifier || email)} />
    </Stack>
  );
};
