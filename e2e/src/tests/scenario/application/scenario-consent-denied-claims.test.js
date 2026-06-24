import { describe, expect, it } from "@jest/globals";

import { getJwks, requestToken } from "../../../api/oauthClient";
import { backendUrl, clientSecretPostClient, serverConfig } from "../../testConfig";
import { requestAuthorizations } from "../../../oauth/request";
import { get } from "../../../lib/http";

/**
 * Claim-level consent: claims the end-user denies on the consent screen (denied_claims on the
 * authorize request) are removed from the grant at authorize time, so the issued UserInfo / ID Token
 * omit them (OIDC4IDA §5.7.3). This covers the standard (scope-derived) claim path; the
 * verified_claims path is covered in spec/oidc_for_identity_assurance.test.js §5.7.3.
 */
describe("Consent: denied claims are omitted from issued tokens (#1653 step②)", () => {
  // Baseline: with profile + email scope the UserInfo holds name, preferred_username and email.
  // Denying "name" must drop only that claim while the other consented claims remain.
  const userinfoWithDeniedClaims = async (deniedClaims) => {
    const { authorizationResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      clientId: clientSecretPostClient.clientId,
      responseType: "code",
      state: `denied-claims-${Date.now()}`,
      scope: "openid profile email " + clientSecretPostClient.scope,
      redirectUri: clientSecretPostClient.redirectUri,
      deniedClaims,
    });
    expect(authorizationResponse.code).not.toBeNull();

    const tokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      code: authorizationResponse.code,
      grantType: "authorization_code",
      redirectUri: clientSecretPostClient.redirectUri,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });
    expect(tokenResponse.status).toBe(200);

    const userinfoResponse = await get({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/userinfo`,
      headers: { Authorization: `Bearer ${tokenResponse.data.access_token}` },
    });
    expect(userinfoResponse.status).toBe(200);
    return userinfoResponse.data;
  };

  it("retains all consented claims when nothing is denied (baseline)", async () => {
    const userinfo = await userinfoWithDeniedClaims([]);
    console.log(JSON.stringify(userinfo, null, 2));
    expect(userinfo.name).toBeDefined();
    expect(userinfo.preferred_username).toBeDefined();
    expect(userinfo.email).toBeDefined();
  });

  it("removes an end-user-denied standard claim from the UserInfo response", async () => {
    const userinfo = await userinfoWithDeniedClaims(["name"]);
    console.log(JSON.stringify(userinfo, null, 2));

    // denied claim omitted
    expect(userinfo.name).toBeUndefined();
    // other consented claims (same profile scope + email scope) retained
    expect(userinfo.preferred_username).toBeDefined();
    expect(userinfo.email).toBeDefined();
  });
});
