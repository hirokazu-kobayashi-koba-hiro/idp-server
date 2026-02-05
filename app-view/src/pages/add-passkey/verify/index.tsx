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
  CircularProgress,
  Alert,
} from "@mui/material";
import { useRouter } from "next/router";
import { backendUrl, useAppContext } from "@/pages/_app";
import { useState } from "react";
import { Email, Key } from "@mui/icons-material";
import { AddPasskeyStepper } from "@/components/AddPasskeyStepper";

interface ApiErrorResponse {
  error?: string;
  error_description?: string;
  message?: string;
}

export default function AddPasskeyVerifyPage() {
  const router = useRouter();
  const { email } = useAppContext();
  const [verificationCode, setVerificationCode] = useState("");
  const [message, setMessage] = useState("");
  const [messageType, setMessageType] = useState<"error" | "success">("error");
  const [loading, setLoading] = useState(false);
  const [resending, setResending] = useState(false);
  const { id, tenant_id: tenantId } = router.query;
  const theme = useTheme();

  const getErrorMessage = (status: number, data: ApiErrorResponse): string => {
    if (data.error_description) {
      return data.error_description;
    }
    if (data.message) {
      return data.message;
    }
    switch (status) {
      case 400:
        return "Invalid verification code. Please check and try again.";
      case 401:
        return "Session expired. Please start over.";
      case 404:
        return "Verification session not found. Please start over.";
      case 429:
        return "Too many attempts. Please wait a moment and try again.";
      default:
        return "Verification failed. Please try again.";
    }
  };

  const handleReSend = async () => {
    setResending(true);
    setMessage("");
    try {
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
        const errorData: ApiErrorResponse = await response.json().catch(() => ({}));
        setMessageType("error");
        setMessage(getErrorMessage(response.status, errorData));
        return;
      }
      setMessageType("success");
      setMessage("Verification code sent! Please check your email.");
    } catch (error) {
      console.error("Resend error:", error);
      setMessageType("error");
      setMessage("Failed to send verification code. Please try again.");
    } finally {
      setResending(false);
    }
  };

  const handleNext = async () => {
    setLoading(true);
    setMessage("");
    try {
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
        const errorData: ApiErrorResponse = await response.json().catch(() => ({}));
        setMessageType("error");
        setMessage(getErrorMessage(response.status, errorData));
        return;
      }

      // Success - proceed to FIDO2 registration
      // Use the same FIDO2 registration page as signup
      router.push(`/signup/fido2?id=${id}&tenant_id=${tenantId}&flow=add-passkey`);
    } catch (error) {
      console.error("Verification error:", error);
      setMessageType("error");
      setMessage("An unexpected error occurred. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  // Redirect to email input if no email in context
  if (!email && typeof window !== "undefined") {
    router.push(`/add-passkey?id=${id}&tenant_id=${tenantId}`);
    return null;
  }

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
        <Box display="flex" alignItems="center" gap={1} mb={1}>
          <Key color="primary" />
          <Typography variant="h5" fontWeight={600}>
            Verify Your Email
          </Typography>
        </Box>
        <Typography variant="body2" color="text.secondary" mb={4}>
          Enter the 6-digit code we sent to <strong>{email}</strong>
        </Typography>

        <Stack spacing={3}>
          <AddPasskeyStepper activeStep={1} />

          <TextField
            label="Verification Code"
            inputMode="numeric"
            placeholder="000000"
            value={verificationCode}
            onChange={(e) => setVerificationCode(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && verificationCode && !loading) {
                handleNext();
              }
            }}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <Email />
                </InputAdornment>
              ),
            }}
          />

          {message && (
            <Alert severity={messageType} onClose={() => setMessage("")}>
              {message}
            </Alert>
          )}

          <Box display="flex" justifyContent="flex-end">
            <Button
              variant="contained"
              onClick={handleNext}
              disabled={!verificationCode || loading}
              sx={{ textTransform: "none" }}
            >
              {loading ? <CircularProgress size={24} color="inherit" /> : "Verify & Continue"}
            </Button>
          </Box>

          <Divider sx={{ my: 2 }} />

          <Box display="flex" justifyContent="center">
            <Link
              onClick={handleReSend}
              sx={{
                cursor: resending ? "not-allowed" : "pointer",
                opacity: resending ? 0.5 : 1,
              }}
              component="button"
              disabled={resending}
            >
              {resending ? "Sending..." : "Didn't get the code? Resend"}
            </Link>
          </Box>
        </Stack>
      </Paper>
    </Container>
  );
}
