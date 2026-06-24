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
import { Phone } from "@mui/icons-material";
import { useAtom } from "jotai";
import { backendUrl } from "@/pages/_app";
import { readErrorMessage } from "@/auth/http";
import { needsContactInput } from "@/auth/stepHelpers";
import { authUserStatusAtom } from "@/state/AuthState";
import { AuthAlert } from "@/components/auth/AuthAlert";
import { OtpInput } from "@/components/auth/OtpInput";
import { ResendLink } from "@/components/auth/ResendLink";
import { StepProps } from "./StepProps";

const CODE_LENGTH = 6;

/**
 * SMS one-time-code step (method "sms").
 *
 * Two phases: when the phone number must be collected ({@link needsContactInput}) the user enters
 * it, then receives the code; otherwise the server resolves the number from the identified user and
 * the code is sent on mount.
 */
export const SmsStep = ({ tenantId, id, step, onCompleted }: StepProps) => {
  const [userStatus] = useAtom(authUserStatusAtom);
  const needsPhoneInput = needsContactInput(step, userStatus);

  const [phone, setPhone] = useState("");
  const [code, setCode] = useState("");
  const [phase, setPhase] = useState<"identify" | "verify">(
    needsPhoneInput ? "identify" : "verify",
  );
  const [loading, setLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const [message, setMessage] = useState("");
  const autoSent = useRef(false);

  const sendChallenge = async (): Promise<boolean> => {
    setSending(true);
    setMessage("");
    try {
      const response = await fetch(
        `${backendUrl}/${tenantId}/v1/authorizations/${id}/sms-authentication-challenge`,
        {
          method: "POST",
          credentials: "include",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(needsPhoneInput ? { phone_number: phone } : {}),
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

  // 2nd-factor step with a number on file: send the code once on mount.
  useEffect(() => {
    if (phase !== "verify" || autoSent.current) return;
    autoSent.current = true;
    void sendChallenge();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleSend = async () => {
    if (!phone) return;
    if (await sendChallenge()) setPhase("verify");
  };

  const handleVerify = async () => {
    setLoading(true);
    setMessage("");
    try {
      const response = await fetch(
        `${backendUrl}/${tenantId}/v1/authorizations/${id}/sms-authentication`,
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
      await onCompleted();
    } finally {
      setLoading(false);
    }
  };

  if (phase === "identify") {
    return (
      <Stack spacing={3}>
        <Typography variant="body2" color="text.secondary">
          Enter your phone number and we&apos;ll text you a verification code.
        </Typography>
        <TextField
          label="Phone number"
          autoFocus
          value={phone}
          onChange={(e) => setPhone(e.target.value)}
          inputProps={{ inputMode: "tel", autoComplete: "tel" }}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <Phone />
              </InputAdornment>
            ),
          }}
        />
        <AuthAlert message={message} />
        <Button
          variant="contained"
          fullWidth
          disabled={sending || !phone}
          onClick={handleSend}
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
        We sent a verification code to your phone. Enter it below to continue.
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
      <ResendLink onResend={sendChallenge} />
    </Stack>
  );
};
