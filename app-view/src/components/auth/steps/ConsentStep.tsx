"use client";

import { useState } from "react";
import { Box, Button, CircularProgress, Stack, Typography } from "@mui/material";
import CheckCircleOutlineIcon from "@mui/icons-material/CheckCircleOutline";
import { backendUrl } from "@/pages/_app";
import { ViewData } from "@/auth/types";

/** Human-readable labels for common scopes; unknown scopes are shown as-is. */
const SCOPE_LABELS: Record<string, string> = {
  profile: "Your basic profile",
  email: "Your email address",
  phone: "Your phone number",
  address: "Your address",
  offline_access: "Keep you signed in",
};

const describeScope = (scope: string): string => SCOPE_LABELS[scope] ?? scope;

type Props = {
  tenantId: string;
  id: string;
  viewData?: ViewData;
};

/**
 * Terminal consent step, shown once the authentication flow status is "success".
 *
 * Allow → `authorize`, Deny → `deny`; both redirect back to the client via the returned
 * `redirect_uri`.
 */
export const ConsentStep = ({ tenantId, id, viewData }: Props) => {
  const [loading, setLoading] = useState(false);

  const clientName = viewData?.client_name ?? "the application";
  // "openid" is a protocol marker, not a user-facing permission.
  const permissions = (viewData?.scopes ?? []).filter(
    (scope) => scope !== "openid",
  );

  const submit = async (action: "authorize" | "deny") => {
    setLoading(true);
    try {
      const response = await fetch(
        `${backendUrl}/${tenantId}/v1/authorizations/${id}/${action}`,
        {
          method: "POST",
          credentials: "include",
          headers: { "Content-Type": "application/json" },
          body:
            action === "authorize"
              ? JSON.stringify({ action: "signup" })
              : undefined,
        },
      );
      const body = await response.json().catch(() => ({}));
      if (body.redirect_uri) window.location.href = body.redirect_uri;
    } finally {
      setLoading(false);
    }
  };

  return (
    <Stack spacing={3}>
      <Typography variant="body2" color="text.secondary">
        Continue to {clientName} to finish signing in.
      </Typography>
      {permissions.length > 0 && (
        <Box
          sx={{
            borderRadius: 2,
            border: "1px solid",
            borderColor: "divider",
            p: 2,
          }}
        >
          <Typography variant="caption" color="text.secondary">
            {clientName} will be able to access
          </Typography>
          <Stack
            component="ul"
            spacing={1}
            sx={{ listStyle: "none", pl: 0, m: 0, mt: 1 }}
          >
            {permissions.map((scope) => (
              <Stack
                key={scope}
                component="li"
                direction="row"
                spacing={1}
                alignItems="center"
              >
                <CheckCircleOutlineIcon fontSize="small" color="success" />
                <Typography variant="body2">{describeScope(scope)}</Typography>
              </Stack>
            ))}
          </Stack>
        </Box>
      )}
      <Box display="flex" justifyContent="space-between" gap={2}>
        <Button
          variant="outlined"
          disabled={loading}
          onClick={() => submit("deny")}
          sx={{ textTransform: "none" }}
          fullWidth
        >
          Cancel
        </Button>
        <Button
          variant="contained"
          disabled={loading}
          onClick={() => submit("authorize")}
          sx={{ textTransform: "none" }}
          fullWidth
        >
          {loading ? <CircularProgress size={24} /> : "Continue"}
        </Button>
      </Box>
    </Stack>
  );
};
