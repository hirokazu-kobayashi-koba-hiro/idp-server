"use client";

import {
  Box,
  Button,
  Container,
  Paper,
  Stack,
  Typography,
  useTheme,
  alpha,
  Link,
  Divider,
} from "@mui/material";
import { useRouter } from "next/router";
import { useQuery } from "@tanstack/react-query";
import { backendUrl } from "@/pages/_app";
import { Loading } from "@/components/Loading";

export default function Authorize() {
  const router = useRouter();
  const { id, tenant_id: tenantId } = router.query;
  const theme = useTheme();

  const { data, isPending } = useQuery({
    queryKey: ["fetchViewData", router.query],
    queryFn: async () => {
      if (!router.isReady || Object.keys(router.query).length === 0) return;
      const response = await fetch(
        `${backendUrl}/${tenantId}/api/v1/authorizations/${id}/view-data`,
        { credentials: "include" },
      );
      if (!response.ok) throw new Error(response.status.toString());
      return await response.json();
    },
  });

  const handleCancel = async () => {
    const response = await fetch(
      `${backendUrl}/${tenantId}/api/v1/authorizations/${id}/deny`,
      {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
      },
    );
    const body = await response.json();
    if (body.redirect_uri) window.location.href = body.redirect_uri;
  };

  const handleApprove = async () => {
    const response = await fetch(
      `${backendUrl}/${tenantId}/api/v1/authorizations/${id}/authorize`,
      {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ action: "signin" }),
      },
    );
    const body = await response.json();
    if (body.redirect_uri) window.location.href = body.redirect_uri;
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
          Authorize Access
        </Typography>
        <Typography variant="body2" color="text.secondary" mb={4}>
          Review the access requested by this application.
        </Typography>

        <Stack spacing={3}>

          <Box>
            <Typography
              variant="subtitle2"
              color="text.secondary"
              gutterBottom
              sx={{ fontWeight: 500 }}
            >
              This app is requesting access to:
            </Typography>

            <Stack spacing={1}>
              {data.scopes.map((scope: string) => (
                <Typography
                  key={scope}
                  variant="body2"
                  sx={{
                    backgroundColor:
                      theme.palette.mode === "light"
                        ? "grey.100"
                        : "rgba(255,255,255,0.05)",
                    px: 2,
                    py: 1,
                    borderRadius: 2,
                    fontSize: "0.85rem",
                    fontWeight: 500,
                  }}
                >
                  {scope}
                </Typography>
              ))}
            </Stack>
          </Box>

          <Stack direction="row" spacing={2} justifyContent="space-between">
            <Button
              variant="outlined"
              color="inherit"
              onClick={handleCancel}
              sx={{ textTransform: "none" }}
              fullWidth
            >
              Cancel
            </Button>
            <Button
              variant="contained"
              color="primary"
              onClick={handleApprove}
              sx={{ textTransform: "none" }}
              fullWidth
            >
              Approve
            </Button>
          </Stack>

          <Divider sx={{ my: 3 }} />

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
