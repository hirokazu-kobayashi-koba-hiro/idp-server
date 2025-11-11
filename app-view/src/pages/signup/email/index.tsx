"use client";

import {
  Box,
  Button,
  Container,
  Divider,
  Link,
  Paper,
  Stack,
  TextField,
  Typography,
  useTheme,
  alpha,
  InputAdornment,
} from "@mui/material";
import { useRouter } from "next/router";
import { backendUrl, useAppContext } from "@/pages/_app";
import { useState } from "react";
import { SignupStepper } from "@/components/SignupStepper";
import { Email } from "@mui/icons-material";

export default function EmailVerificationPage() {
  const router = useRouter();
  const { email } = useAppContext()
  const [verificationCode, setVerificationCode] = useState("");
  const [message, setMessage] = useState("");
  const { id, tenant_id: tenantId } = router.query;
  const theme = useTheme();

  const handleReSend = async () => {
    const response = await fetch(
      `${backendUrl}/${tenantId}/v1/authorizations/${id}/email-authentication-challenge`,
      {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: email }),
      },
    );
    if (!response.ok) {
      throw new Error("sending email verification code is failed");
    }
  };

  const handleNext = async () => {
    const response = await fetch(
      `${backendUrl}/${tenantId}/v1/authorizations/${id}/email-authentication`,
      {
        method: "POST",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          verification_code: verificationCode,
        }),
      },
    );

    if (!response.ok) {
      setMessage("failed email verification");
    }
    router.push(`/signup/fido2?id=${id}&tenant_id=${tenantId}`);
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
          Email Verification
        </Typography>
        <Typography variant="body2" color="text.secondary" mb={4}>
          Enter the 6-digit code we sent to your email.
        </Typography>

        <Stack spacing={3}>
          <SignupStepper activeStep={1} />

          <TextField
            label="Verification Code"
            inputMode="numeric"
            placeholder="000000"
            value={verificationCode}
            onChange={(e) => setVerificationCode(e.target.value)}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <Email />
                </InputAdornment>
              ),
            }}
          />

          {message && (
            <Typography mt={2} color="error" align="center">
              {message}
            </Typography>
          )}

          <Box display="flex" justifyContent="flex-end">
            <Button
              variant="contained"
              onClick={handleNext}
              disabled={!verificationCode}
              sx={{ textTransform: "none" }}
            >
              Next
            </Button>
          </Box>

          <Divider sx={{ my: 2 }} />

          <Box display="flex" justifyContent="center">
            <Link onClick={handleReSend} sx={{ cursor: "pointer" }}>
              Didn’t get the code? Resend
            </Link>
          </Box>
        </Stack>
      </Paper>
    </Container>
  );
}
