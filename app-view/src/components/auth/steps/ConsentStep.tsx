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
import { ClaimValue, ViewData } from "@/auth/types";

/** Human-readable labels for common scopes; unknown scopes are shown as-is. */
const SCOPE_LABELS: Record<string, string> = {
  profile: "Your basic profile",
  email: "Your email address",
  phone: "Your phone number",
  address: "Your address",
  offline_access: "Keep you signed in",
};

/** Scope prefix that releases a user custom property as a claim of the same name (backend). */
const CLAIMS_SCOPE_PREFIX = "claims:";

const humanizeClaim = (name: string): string =>
  name.replace(/_/g, " ").replace(/^\w/, (c) => c.toUpperCase());

const claimNameOf = (scope: string): string | undefined =>
  scope.startsWith(CLAIMS_SCOPE_PREFIX)
    ? scope.slice(CLAIMS_SCOPE_PREFIX.length)
    : undefined;

const describeScope = (scope: string): string => {
  const claim = claimNameOf(scope);
  return SCOPE_LABELS[scope] ?? (claim ? humanizeClaim(claim) : scope);
};

/**
 * A label for one selectable element.
 *
 * An element can be an object (an account with its branch, a card with its brand), which has no
 * natural single label, so its own scalar fields are shown as `field: value` pairs. Nested objects
 * are left out — they would not fit on one line, and the fields that identify an element to a
 * person are scalars.
 */
const describeClaimValue = (value: ClaimValue): string => {
  if (typeof value !== "object" || value === null) return String(value);

  const fields = Object.entries(value).filter(
    ([, field]) => field === null || typeof field !== "object",
  );
  if (fields.length === 0) return JSON.stringify(value);

  return fields
    .map(([name, field]) => `${humanizeClaim(name)}: ${field}`)
    .join(" · ");
};

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

type ScopeItem = {
  scope: string;
  label: string;
  /** Set when the scope releases an array custom property the user can pick elements from. */
  claim?: string;
  values?: ClaimValue[];
};

/**
 * The permissions being granted, each with the individual values it would release (backend #1816).
 *
 * The values are nested under their scope rather than listed separately because they are the same
 * permission at a finer grain: unchecking `claims:accounts` releases no account at all, so its
 * elements are shown disabled instead of disappearing — the row above explains why they no longer
 * apply.
 *
 * Elements are tracked by position, not by value: an element can be an object, and two elements
 * can look alike, so position is the only stable identity. Declined positions are tracked (rather
 * than kept ones) so everything starts consented, the same default as the checkboxes above.
 */
const PermissionSection = ({
  items,
  deniedScopes,
  deniedClaimValues,
  onToggleScope,
  onToggleClaimValue,
}: {
  items: ScopeItem[];
  deniedScopes: Set<string>;
  deniedClaimValues: Record<string, Set<number>>;
  onToggleScope: (scope: string) => void;
  onToggleClaimValue: (claim: string, index: number) => void;
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
        Permissions
      </Typography>
      <FormGroup>
        {items.map((item) => {
          const scopeDenied = deniedScopes.has(item.scope);
          const deniedValues = item.claim
            ? (deniedClaimValues[item.claim] ?? new Set<number>())
            : new Set<number>();
          return (
            <Box key={item.scope}>
              <FormControlLabel
                sx={{ my: -0.25 }}
                control={
                  <Checkbox
                    size="small"
                    checked={!scopeDenied}
                    onChange={() => onToggleScope(item.scope)}
                  />
                }
                label={<Typography variant="body2">{item.label}</Typography>}
              />
              {item.claim && item.values && (
                <FormGroup
                  sx={{ pl: 3.5 }}
                  data-testid={`claim-values-${item.claim}`}
                >
                  {item.values.map((value, index) => (
                    <FormControlLabel
                      key={index}
                      disabled={scopeDenied}
                      sx={{ my: -0.5 }}
                      control={
                        <Checkbox
                          size="small"
                          inputProps={
                            {
                              "data-testid": `claim-value-${item.claim}-${index}`,
                            } as never
                          }
                          checked={!scopeDenied && !deniedValues.has(index)}
                          onChange={() =>
                            onToggleClaimValue(item.claim as string, index)
                          }
                        />
                      }
                      label={
                        <Typography variant="body2" color="text.secondary">
                          {describeClaimValue(value)}
                        </Typography>
                      }
                    />
                  ))}
                </FormGroup>
              )}
            </Box>
          );
        })}
      </FormGroup>
    </Box>
  );
};

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
  const [deniedClaimValues, setDeniedClaimValues] = useState<
    Record<string, Set<number>>
  >({});

  const clientName = viewData?.client_name ?? "the application";
  const claimValues = viewData?.claim_values ?? {};
  // "openid" is a protocol marker, not a user-facing/declinable permission.
  const scopeItems: ScopeItem[] = (viewData?.scopes ?? [])
    .filter((scope) => scope !== "openid")
    .map((scope) => {
      const claim = claimNameOf(scope);
      const values = claim ? claimValues[claim] : undefined;
      return {
        scope,
        label: describeScope(scope),
        ...(values && values.length > 0 ? { claim, values } : {}),
      };
    });

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

  const hasConsentItems =
    scopeItems.length > 0 ||
    standardItems.length > 0 ||
    verifiedItems.length > 0;

  const toggleClaimValue = (claim: string, index: number) =>
    setDeniedClaimValues((prev) => {
      const next = new Set(prev[claim] ?? []);
      if (next.has(index)) next.delete(index);
      else next.add(index);
      return { ...prev, [claim]: next };
    });

  /**
   * The kept elements, sent only for claims where something was actually declined.
   *
   * Omitting untouched claims keeps the request minimal and preserves the server default of
   * releasing everything. A claim whose elements were all declined is sent as an empty list, which
   * the server treats the same as denying the claim whole. A claim whose scope was declined is
   * left out entirely — `denied_scopes` already removes it, and its element checkboxes are
   * disabled rather than cleared, so a selection made before declining must not leak out.
   *
   * Elements are echoed exactly as they were received: the server matches whole elements, so a
   * value rebuilt from its label would match nothing.
   */
  const grantedClaimValues = (): Record<string, ClaimValue[]> =>
    Object.fromEntries(
      scopeItems
        .filter((item) => item.claim && item.values)
        .filter((item) => !deniedScopes.has(item.scope))
        .filter((item) => (deniedClaimValues[item.claim!]?.size ?? 0) > 0)
        .map((item) => [
          item.claim,
          item.values!.filter(
            (_, index) => !deniedClaimValues[item.claim!].has(index),
          ),
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
            <PermissionSection
              items={scopeItems}
              deniedScopes={deniedScopes}
              deniedClaimValues={deniedClaimValues}
              onToggleScope={toggle(setDeniedScopes)}
              onToggleClaimValue={toggleClaimValue}
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
