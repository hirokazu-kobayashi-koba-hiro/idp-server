"use client";

import {
  Box,
  Button,
  Container,
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

export default function AddPasskeyPage() {
  const router = useRouter();
  const { email, setEmail } = useAppContext();
  const [localEmail, setLocalEmail] = useState(email || "");
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);
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
        return "Invalid email address. Please check and try again.";
      case 404:
        return "User not found. Please check your email address.";
      case 429:
        return "Too many attempts. Please wait a moment and try again.";
      default:
        return "Failed to send verification code. Please try again.";
    }
  };

  const validateEmail = (email: string): boolean => {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
  };

  const handleNext = async () => {
    if (!validateEmail(localEmail)) {
      setMessage("Please enter a valid email address.");
      return;
    }

    setLoading(true);
    setMessage("");

    try {
      // Send verification code
      const response = await fetch(
        `${backendUrl}/${tenantId}/v1/authorizations/${id}/email-authentication-challenge`,
        {
          method: "POST",
          credentials: "include",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ email: localEmail }),
        },
      );

      if (!response.ok) {
        const errorData: ApiErrorResponse = await response.json().catch(() => ({}));
        setMessage(getErrorMessage(response.status, errorData));
        return;
      }

      // Store email in context and navigate to verification page
      setEmail(localEmail);
      router.push(`/add-passkey/verify?id=${id}&tenant_id=${tenantId}`);
    } catch (error) {
      console.error("Send verification code error:", error);
      setMessage("An unexpected error occurred. Please try again.");
    } finally {
      setLoading(false);
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
        <Box display="flex" alignItems="center" gap={1} mb={1}>
          <Key color="primary" />
          <Typography variant="h5" fontWeight={600}>
            Add a New Passkey
          </Typography>
        </Box>
        <Typography variant="body2" color="text.secondary" mb={4}>
          Add a backup passkey to your account for secure, passwordless sign-in from another device.
        </Typography>

        <Stack spacing={3}>
          <AddPasskeyStepper activeStep={0} />

          <TextField
            name="email"
            label="Email Address"
            placeholder="you@example.com"
            inputMode="email"
            value={localEmail}
            required
            onChange={(e) => setLocalEmail(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && localEmail && !loading) {
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
            <Alert severity="error" onClose={() => setMessage("")}>
              {message}
            </Alert>
          )}

          <Box display="flex" justifyContent="flex-end">
            <Button
              variant="contained"
              onClick={handleNext}
              disabled={!localEmail || loading}
              sx={{ textTransform: "none" }}
            >
              {loading ? <CircularProgress size={24} color="inherit" /> : "Send Verification Code"}
            </Button>
          </Box>
        </Stack>
      </Paper>
    </Container>
  );
}
