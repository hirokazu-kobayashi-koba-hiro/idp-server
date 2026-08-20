"use client";

import { useState } from "react";
import {
  Box,
  Button,
  Checkbox,
  CircularProgress,
  FormControlLabel,
  FormGroup,
  Stack,
  Typography,
} from "@mui/material";
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

const humanizeClaim = (name: string): string =>
  name.replace(/_/g, " ").replace(/^\w/, (c) => c.toUpperCase());

type ConsentItem = { value: string; label: string };

const ConsentSection = ({
  title,
  items,
  denied,
  onToggle,
}: {
  title: string;
  items: ConsentItem[];
  denied: Set<string>;
  onToggle: (value: string) => void;
}) => {
  if (items.length === 0) return null;
  return (
    <Box>
      <Typography
        variant="caption"
        color="text.secondary"
        fontWeight={600}
        display="block"
      >
        {title}
      </Typography>
      <FormGroup>
        {items.map((item) => (
          <FormControlLabel
            key={item.value}
            sx={{ my: -0.25 }}
            control={
              <Checkbox
                size="small"
                checked={!denied.has(item.value)}
                onChange={() => onToggle(item.value)}
              />
            }
            label={<Typography variant="body2">{item.label}</Typography>}
          />
        ))}
      </FormGroup>
    </Box>
  );
};

/**
 * Per-element consent for an array claim (backend #1816).
 *
 * The value list comes from `view-data.claim_values`, which the server only fills once the flow has
 * an authenticated user. Unchecking elements sends the kept ones as `granted_claim_values`; the
 * server intersects them with what the user actually owns, so this can only take away.
 */
const ClaimValueSection = ({
  claim,
  values,
  denied,
  onToggle,
}: {
  claim: string;
  values: string[];
  denied: Set<string>;
  onToggle: (value: string) => void;
}) => (
  <Box>
    <Typography
      variant="caption"
      color="text.secondary"
      fontWeight={600}
      display="block"
    >
      {humanizeClaim(claim)}
    </Typography>
    <FormGroup data-testid={`claim-values-${claim}`}>
      {values.map((value) => (
        <FormControlLabel
          key={value}
          sx={{ my: -0.25 }}
          control={
            <Checkbox
              size="small"
              inputProps={
                { "data-testid": `claim-value-${claim}-${value}` } as never
              }
              checked={!denied.has(value)}
              onChange={() => onToggle(value)}
            />
          }
          label={<Typography variant="body2">{value}</Typography>}
        />
      ))}
    </FormGroup>
  </Box>
);

type Props = {
  tenantId: string;
  id: string;
  viewData?: ViewData;
};

/**
 * Terminal consent step, shown once the authentication flow status is "success".
 *
 * Allow → `authorize`, Cancel → `deny`; both redirect back via the returned `redirect_uri`.
 * Per-scope and per-claim checkboxes let the user decline individual items; declined names are
 * sent as `denied_scopes` / `denied_claims` and removed from the grant (OIDC4IDA §5.7.3 for
 * claims; scope removal merges with policy-enforced denials server-side).
 */
export const ConsentStep = ({ tenantId, id, viewData }: Props) => {
  const [loading, setLoading] = useState(false);
  const [deniedScopes, setDeniedScopes] = useState<Set<string>>(new Set());
  const [deniedClaims, setDeniedClaims] = useState<Set<string>>(new Set());
  // Declined elements are tracked rather than kept ones so that everything starts consented, the
  // same default as the scope and claim checkboxes.
  const [deniedClaimValues, setDeniedClaimValues] = useState<
    Record<string, Set<string>>
  >({});

  const clientName = viewData?.client_name ?? "the application";
  // "openid" is a protocol marker, not a user-facing/declinable permission.
  const scopeItems: ConsentItem[] = (viewData?.scopes ?? [])
    .filter((scope) => scope !== "openid")
    .map((scope) => ({ value: scope, label: describeScope(scope) }));

  const claims = viewData?.claims;
  // "sub" is the essential subject identifier and is never deniable.
  const verifiedNames = (claims?.verified_claims ?? []).filter(
    (name) => name !== "sub",
  );
  const verifiedSet = new Set(verifiedNames);
  const standardNames = Array.from(
    new Set([...(claims?.id_token ?? []), ...(claims?.userinfo ?? [])]),
  ).filter((name) => name !== "sub" && !verifiedSet.has(name));

  const standardItems = standardNames.map((name) => ({
    value: name,
    label: humanizeClaim(name),
  }));
  const verifiedItems = verifiedNames.map((name) => ({
    value: name,
    label: humanizeClaim(name),
  }));

  // Only claims that were not declined whole are selectable per element — unchecking the claim
  // itself already removes everything, so keeping its element list on screen would be misleading.
  const claimValueEntries = Object.entries(viewData?.claim_values ?? {}).filter(
    ([claim]) => !deniedClaims.has(claim),
  );

  const hasConsentItems =
    scopeItems.length > 0 ||
    standardItems.length > 0 ||
    verifiedItems.length > 0 ||
    claimValueEntries.length > 0;

  const toggleClaimValue = (claim: string) => (value: string) =>
    setDeniedClaimValues((prev) => {
      const next = new Set(prev[claim] ?? []);
      if (next.has(value)) next.delete(value);
      else next.add(value);
      return { ...prev, [claim]: next };
    });

  /**
   * The kept elements, sent only for claims where something was actually declined.
   *
   * Omitting untouched claims keeps the request minimal and preserves the server default of
   * releasing everything. A claim whose elements were all declined is sent as an empty list, which
   * the server treats the same as denying the claim whole.
   */
  const grantedClaimValues = (): Record<string, string[]> =>
    Object.fromEntries(
      claimValueEntries
        .filter(([claim]) => (deniedClaimValues[claim]?.size ?? 0) > 0)
        .map(([claim, values]) => [
          claim,
          values.filter((value) => !deniedClaimValues[claim].has(value)),
        ]),
    );

  const toggle =
    (setter: typeof setDeniedScopes) => (value: string) =>
      setter((prev) => {
        const next = new Set(prev);
        if (next.has(value)) next.delete(value);
        else next.add(value);
        return next;
      });

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
              ? JSON.stringify({
                  action: "signup",
                  denied_scopes: Array.from(deniedScopes),
                  denied_claims: Array.from(deniedClaims),
                  granted_claim_values: grantedClaimValues(),
                })
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

      {hasConsentItems && (
        <Box
          sx={{
            borderRadius: 2,
            border: "1px solid",
            borderColor: "divider",
            p: 2,
          }}
        >
          <Typography variant="caption" color="text.secondary">
            Choose what to share with {clientName}
          </Typography>
          <Stack spacing={1.5} mt={1}>
            <ConsentSection
              title="Permissions"
              items={scopeItems}
              denied={deniedScopes}
              onToggle={toggle(setDeniedScopes)}
            />
            <ConsentSection
              title="Profile information"
              items={standardItems}
              denied={deniedClaims}
              onToggle={toggle(setDeniedClaims)}
            />
            <ConsentSection
              title="Verified information"
              items={verifiedItems}
              denied={deniedClaims}
              onToggle={toggle(setDeniedClaims)}
            />
            {claimValueEntries.map(([claim, values]) => (
              <ClaimValueSection
                key={claim}
                claim={claim}
                values={values}
                denied={deniedClaimValues[claim] ?? new Set()}
                onToggle={toggleClaimValue(claim)}
              />
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
