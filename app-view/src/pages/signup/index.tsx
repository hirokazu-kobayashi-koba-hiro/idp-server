"use client";

import {
  Box,
  Button,
  Container,
  Divider,
  IconButton,
  InputAdornment,
  Link,
  Paper,
  Stack,
  TextField,
  Typography,
  useTheme,
  alpha,
} from "@mui/material";
import { useState } from "react";
import { backendUrl, useAppContext } from "@/pages/_app";
import { useRouter } from "next/router";
import { Email, Lock, Visibility, VisibilityOff } from "@mui/icons-material";
import { SignupStepper } from "@/components/SignupStepper";
import { useQuery } from "@tanstack/react-query";
import { Loading } from "@/components/Loading";

export default function SignUpPage() {
  const { email, setEmail } = useAppContext();
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [errors, setErrors] = useState<{ email?: string; password?: string }>(
    {},
  );
  const [message, setMessage] = useState("");
  const router = useRouter();
  const { setUserId } = useAppContext();
  const { id, tenant_id: tenantId } = router.query;
  const theme = useTheme();

  const { data, isPending } = useQuery({
    queryKey: ["fetchViewData", router.query],
    queryFn: async () => {
      if (!router.isReady || Object.keys(router.query).length === 0) return;

      const response = await fetch(
        `${backendUrl}/${tenantId}/v1/authorizations/${id}/view-data`,
        { credentials: "include" },
      );

      if (!response.ok) throw new Error(response.status.toString());

      const viewData = await response.json();

      const { custom_params: customParams } = viewData;
      console.log(customParams);

      if (customParams && customParams.invitation_id && customParams.invitation_tenant_id) {
        const invitationResponse = await fetch(
          `${backendUrl}/${customParams.invitation_tenant_id}/v1/invitations/${customParams.invitation_id}`,
          {
            credentials: "include",
          },
        );
        const invitationData = await invitationResponse.json();

        setEmail(invitationData.email);

        return {
          ...viewData,
          invitation: invitationData,
        };
      }

      return viewData;
    },
  });

  const validate = () => {
    const newErrors: typeof errors = {};
    if (!email || !email.includes("@")) {
      newErrors.email = "Please enter a valid email address.";
    }
    if (!password || password.length < 8) {
      newErrors.password = "Password must be at least 8 characters.";
    }
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleClick = async () => {
    if (!validate()) return;

    const roleId = data?.invitation?.role_id || "";
    const roleName = data?.invitation?.role_name || "";
    const invitationTenantId = data?.invitation?.tenant_id || "";

    const response = await fetch(
      `${backendUrl}/${tenantId}/v1/authorizations/${id}/password-registration`,
      {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name: email, email: email, password, role_id: roleId, role_name: roleName, tenant_id: invitationTenantId }),
      },
    );

    if (!response.ok) {
      console.error(response.status);
      setMessage("failed signup request");
      return;
    }

    const body = await response.json();
    setUserId(body.id);

    const sendingEmailResponse = await fetch(
      `${backendUrl}/${tenantId}/v1/authorizations/${id}/email-authentication-challenge`,
      {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: email }),
      },
    );

    if (!sendingEmailResponse.ok) {
      console.error("sending email verification code is failed");
      setMessage("sending email verification code is failed");
    }

    router.push(`/signup/email?id=${id}&tenant_id=${tenantId}`);
  };

  if (isPending || !data) return <Loading />;

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
          IdP Server Sign Up
        </Typography>
        {data.invitation ? (
          <>
            <Typography variant="body2" color="text.secondary" mb={4}>
              {
                `You have been invited to join the ${data.invitation.tenant_name}. Please enter your password to continue.`
              }
            </Typography>
        </>
        ): (
         <>
           <Typography variant="body2" color="text.secondary" mb={4}>
             {
               "Create your account to continue. You'll verify your email in the next step."
             }
           </Typography>
         </>
        )}

        <Stack spacing={3}>
          <SignupStepper activeStep={0} />

          <TextField
            name="email"
            label="Email"
            placeholder="you@example.com"
            inputMode="email"
            value={email}
            disabled={!!data?.invitation}
            required
            error={Boolean(errors.email)}
            helperText={errors.email}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <Email />
                </InputAdornment>
              ),
            }}
            onChange={(e) => setEmail(e.target.value)}
          />

          <TextField
            name="password"
            label="Password"
            type={showPassword ? "text" : "password"}
            value={password}
            required
            error={Boolean(errors.password)}
            helperText={errors.password}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <Lock />
                </InputAdornment>
              ),
              endAdornment: (
                <InputAdornment position="end">
                  <IconButton
                    onClick={() => setShowPassword((prev) => !prev)}
                    edge="end"
                  >
                    {showPassword ? <VisibilityOff /> : <Visibility />}
                  </IconButton>
                </InputAdornment>
              ),
            }}
            onChange={(e) => setPassword(e.target.value)}
          />
          {message && (
            <Typography mt={2} color="error" align="center">
              {message}
            </Typography>
          )}

          <Box display="flex" justifyContent="flex-end">
            <Button
              variant="contained"
              disabled={!email || !password}
              onClick={handleClick}
              sx={{ textTransform: "none" }}
            >
              Next
            </Button>
          </Box>

          <Divider sx={{ my: 2 }} />

          <Typography variant="caption" color="text.secondary" align="center">
            By continuing, you agree to our
            <Link href={data.tos_uri} sx={{ fontWeight: 600, mx: 0.5 }}>
              Terms of Use
            </Link>
            and
            <Link href={data.policy_uri} sx={{ fontWeight: 600, mx: 0.5 }}>
              Privacy Policy
            </Link>
            .
          </Typography>
        </Stack>
      </Paper>
    </Container>
  );
}
