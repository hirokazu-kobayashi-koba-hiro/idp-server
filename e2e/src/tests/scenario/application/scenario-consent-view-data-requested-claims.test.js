import { describe, expect, it } from "@jest/globals";

import { backendUrl, clientSecretPostClient, serverConfig } from "../../testConfig";
import { get } from "../../../lib/http";

/**
 * Consent view-data exposes the requested claims so the consent UI can present them per-claim.
 *
 * This is the foundation for claim-level consent (OIDC4IDA §5.7.3 "omit non-consented data"):
 * before the user can consent to / deny individual claims, the authorization view-data must tell
 * the UI which claims are being requested. The `claims` member mirrors token/userinfo issuance:
 *   - claims.id_token / claims.userinfo : standard claim names resolved from scope + claims param
 *   - claims.verified_claims            : verified claim names requested via the `claims` parameter
 *
 * verified_claims requested via `verified_claims:*` scopes remain visible in `scopes`.
 */
const startAuthorization = async ({ scope, claims }) => {
  const params = new URLSearchParams({
    response_type: "code",
    client_id: clientSecretPostClient.clientId,
    redirect_uri: clientSecretPostClient.redirectUri,
    scope,
    state: `view-data-claims-${Date.now()}`,
  });
  if (claims) {
    params.set("claims", claims);
  }

  const authorizeResponse = await get({
    url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations?${params.toString()}`,
    headers: {},
  });
  expect(authorizeResponse.status).toBe(302);

  const location = authorizeResponse.headers.location;
  const authId = new URL(location, backendUrl).searchParams.get("id");
  expect(authId).toBeTruthy();

  const viewDataResponse = await get({
    url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${authId}/view-data`,
    headers: {},
  });
  expect(viewDataResponse.status).toBe(200);
  return viewDataResponse.data;
};

describe("Consent view-data exposes requested claims (foundation for OIDC4IDA §5.7.3 claim-level consent)", () => {
  it("surfaces scope-derived standard claims under claims.userinfo (and id_token / verified_claims keys)", async () => {
    const viewData = await startAuthorization({ scope: "openid profile email" });
    console.log(JSON.stringify(viewData.claims, null, 2));

    expect(viewData.claims).toBeDefined();
    expect(Array.isArray(viewData.claims.id_token)).toBe(true);
    expect(Array.isArray(viewData.claims.userinfo)).toBe(true);
    expect(Array.isArray(viewData.claims.verified_claims)).toBe(true);

    // userinfo claims are scope-driven (independent of id_token strict mode):
    // profile scope -> given_name / family_name ; email scope -> email
    expect(viewData.claims.userinfo).toEqual(
      expect.arrayContaining(["given_name", "family_name", "email"])
    );
    // address scope not requested -> absent
    expect(viewData.claims.userinfo).not.toContain("address");
    // no claims parameter / verified_claims:* scope -> no verified claims
    expect(viewData.claims.verified_claims).toHaveLength(0);
  });

  it("surfaces verified_claims requested via the claims parameter under claims.verified_claims", async () => {
    const claims = JSON.stringify({
      id_token: {
        verified_claims: {
          verification: { trust_framework: null },
          claims: { given_name: null, family_name: null },
        },
      },
    });
    const viewData = await startAuthorization({ scope: "openid", claims });
    console.log(JSON.stringify(viewData.claims, null, 2));

    expect(viewData.claims.verified_claims).toEqual(
      expect.arrayContaining(["given_name", "family_name"])
    );
    // openid-only scope -> no standard profile/email claims
    expect(viewData.claims.id_token).not.toContain("email");
  });
});
