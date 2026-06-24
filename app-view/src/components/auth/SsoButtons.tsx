"use client";

import { useState } from "react";
import { Button, Stack, Typography } from "@mui/material";
import Image from "next/image";
import { backendUrl } from "@/pages/_app";
import { Federation } from "@/auth/types";

/** Display label / logo per known SSO provider; unknown providers fall back to a capitalized name. */
const PROVIDER_META: Record<string, { label: string; logo?: string }> = {
  google: { label: "Google", logo: "/logos/google.svg" },
  yahoo: { label: "Yahoo! JAPAN", logo: "/logos/yahoo_japan_icon_64.png" },
};

const labelFor = (provider: string): string =>
  PROVIDER_META[provider]?.label ??
  provider.charAt(0).toUpperCase() + provider.slice(1);

type Props = {
  tenantId: string;
  id: string;
  federations: Federation[];
};

/**
 * Federated sign-in buttons, driven by view-data `available_federations`.
 *
 * Each button starts the federation by POSTing to
 * `/{tenant}/v1/authorizations/{id}/federations/{type}/{sso_provider}` and following the returned
 * redirect_uri to the upstream IdP.
 */
export const SsoButtons = ({ tenantId, id, federations }: Props) => {
  const [pending, setPending] = useState<string | null>(null);
  const [message, setMessage] = useState("");

  const start = async (federation: Federation) => {
    setPending(federation.sso_provider);
    setMessage("");
    try {
      const response = await fetch(
        `${backendUrl}/${tenantId}/v1/authorizations/${id}/federations/${federation.type}/${federation.sso_provider}`,
        {
          method: "POST",
          credentials: "include",
          headers: { "Content-Type": "application/json" },
        },
      );
      const body = await response.json().catch(() => ({}));
      if (response.ok && body.redirect_uri) {
        window.location.href = body.redirect_uri;
        return;
      }
      setMessage("We couldn't start that sign-in. Please try again.");
    } catch {
      setMessage("We couldn't start that sign-in. Please try again.");
    } finally {
      setPending(null);
    }
  };

  return (
    <Stack spacing={1.5}>
      {federations.map((federation) => {
        const meta = PROVIDER_META[federation.sso_provider];
        return (
          <Button
            key={federation.id ?? `${federation.type}-${federation.sso_provider}`}
            variant="outlined"
            color="inherit"
            fullWidth
            disabled={pending !== null}
            onClick={() => start(federation)}
            startIcon={
              meta?.logo ? (
                <Image src={meta.logo} alt="" width={18} height={18} />
              ) : undefined
            }
            sx={{
              justifyContent: "center",
              borderColor: "divider",
              color: "text.primary",
              py: 1.1,
            }}
          >
            Continue with {labelFor(federation.sso_provider)}
          </Button>
        );
      })}
      {message && (
        <Typography variant="caption" color="error">
          {message}
        </Typography>
      )}
    </Stack>
  );
};
