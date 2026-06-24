"use client";

import { Box, Stack, Typography, alpha } from "@mui/material";
import { useRouter } from "next/router";
import ErrorOutlineRoundedIcon from "@mui/icons-material/ErrorOutlineRounded";
import { AuthCard } from "@/components/auth/AuthCard";
import { Loading } from "@/components/Loading";

const asString = (value: string | string[] | undefined): string =>
  Array.isArray(value) ? (value[0] ?? "") : (value ?? "");

const ErrorBadge = () => (
  <Box
    sx={{
      width: 48,
      height: 48,
      borderRadius: 2.5,
      display: "grid",
      placeItems: "center",
      color: "error.main",
      bgcolor: (theme) => alpha(theme.palette.error.main, 0.1),
    }}
  >
    <ErrorOutlineRoundedIcon />
  </Box>
);

const DetailField = ({ label, value }: { label: string; value: string }) => (
  <Box textAlign="left">
    <Typography
      variant="caption"
      color="text.secondary"
      fontWeight={600}
      display="block"
      gutterBottom
    >
      {label}
    </Typography>
    <Typography
      variant="body2"
      sx={{
        px: 2,
        py: 1,
        borderRadius: 2,
        bgcolor: "background.default",
        border: "1px solid",
        borderColor: "divider",
        fontFamily: "monospace",
        wordBreak: "break-word",
      }}
    >
      {value}
    </Typography>
  </Box>
);

export default function AuthorizationError() {
  const router = useRouter();

  if (!router.isReady) return <Loading />;

  const error = asString(router.query.error) || "unknown_error";
  const errorDescription =
    asString(router.query.error_description) || "No description provided.";

  return (
    <AuthCard
      icon={<ErrorBadge />}
      title="Authorization failed"
      subtitle="We couldn't complete the authorization request."
    >
      <Stack spacing={2}>
        <DetailField label="Error" value={error} />
        <DetailField label="Description" value={errorDescription} />
      </Stack>
    </AuthCard>
  );
}
