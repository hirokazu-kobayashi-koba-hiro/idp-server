"use client";

import { useState } from "react";
import { Box, Button, CircularProgress, Stack, Typography } from "@mui/material";
import { backendUrl } from "@/pages/_app";
import { ViewData } from "@/auth/types";

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
        Continue to {viewData?.client_name ?? "the application"}.
      </Typography>
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
