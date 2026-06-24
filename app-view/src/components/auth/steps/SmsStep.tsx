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
import { backendUrl } from "@/pages/_app";
import { readErrorMessage } from "@/auth/http";
import { AuthAlert } from "@/components/auth/AuthAlert";
import { OtpInput } from "@/components/auth/OtpInput";
import { StepProps } from "./StepProps";

const CODE_LENGTH = 6;

/**
 * SMS one-time-code step (method "sms").
 *
 * Challenge + verification-code is the same ceremony whether the step registers a phone number or
 * authenticates an existing one, so a single component covers both. For a 2nd-factor step the
 * server resolves the phone number from the identified user, so the challenge body can be empty.
 */
export const SmsStep = ({ tenantId, id, step, onCompleted }: StepProps) => {
  const [phone, setPhone] = useState("");
  const [code, setCode] = useState("");
  const [loading, setLoading] = useState(false);
  const [resending, setResending] = useState(false);
  const [message, setMessage] = useState("");
  const challengeSent = useRef(false);

  // A 1st-factor step identifies the user, so the phone number must be entered here; a 2nd-factor
  // step verifies the already-identified user, so the server resolves the number.
  const needsPhoneInput = step.requires_user === false;

  const sendChallenge = async () => {
    if (needsPhoneInput && !phone) return;
    setResending(true);
    try {
      await fetch(
        `${backendUrl}/${tenantId}/v1/authorizations/${id}/sms-authentication-challenge`,
        {
          method: "POST",
          credentials: "include",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(needsPhoneInput ? { phone_number: phone } : {}),
        },
      );
    } finally {
      setResending(false);
    }
  };

  useEffect(() => {
    if (challengeSent.current || needsPhoneInput) return;
    challengeSent.current = true;
    void sendChallenge();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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

  return (
    <Stack spacing={3}>
      <Typography variant="body2" color="text.secondary">
        {needsPhoneInput
          ? "Enter your phone number and we'll text you a verification code."
          : "We sent a verification code to your phone. Enter it below to continue."}
      </Typography>
      {needsPhoneInput && (
        <TextField
          label="Phone number"
          autoFocus
          value={phone}
          onChange={(e) => setPhone(e.target.value)}
          inputProps={{ inputMode: "tel", autoComplete: "tel" }}
        />
      )}
      <OtpInput
        value={code}
        onChange={setCode}
        length={CODE_LENGTH}
        autoFocus={!needsPhoneInput}
      />
      <AuthAlert message={message} />
      <Box display="flex" justifyContent="space-between" alignItems="center">
        <Button
          variant="text"
          disabled={resending}
          onClick={sendChallenge}
          sx={{ textTransform: "none" }}
        >
          {resending ? "Sending..." : "Send code"}
        </Button>
        <Button
          variant="contained"
          disabled={loading || code.length < CODE_LENGTH}
          onClick={handleVerify}
          sx={{ textTransform: "none" }}
        >
          {loading ? <CircularProgress size={24} /> : "Verify"}
        </Button>
      </Box>
    </Stack>
  );
};
