import { describe, expect, it } from "@jest/globals";

import { get, postWithJson } from "../../../lib/http";
import { backendUrl, serverConfig } from "../../testConfig";

/**
 * Delegates the authentication of an authorization code flow to a CIBA flow, using the external
 * API authentication interactor. No idp-server code is involved beyond configuration: the three
 * interactions of the `ciba-delegation` authentication config drive the CIBA endpoints over HTTP.
 *
 * The external authorization server is this same test tenant, so the whole delegation runs without
 * a third party.
 *
 * Fixtures (config/examples/e2e/test-tenant):
 *  - clients/cibaDelegationClient.json
 *  - authentication-config/external-api/ciba-delegation.json
 *  - authentication-policy/oauth.json (ciba_delegation_policy / ciba_delegation_mfa_policy)
 */
describe("oauth - ciba delegation via external api authentication", () => {
  const tenantId = serverConfig.tenantId;
  const clientId = "cibaDelegationClient";
  const redirectUri = "https://www.certification.openid.net/test/a/idp_oidc_basic/callback";
  const cibaLoginHint = serverConfig.ciba.loginHint;
  const deviceId = serverConfig.ciba.authenticationDeviceId;

  const authorize = async (extraParams = {}) => {
    const response = await get({
      url:
        `${backendUrl}/${tenantId}/v1/authorizations?` +
        new URLSearchParams({
          response_type: "code",
          client_id: clientId,
          redirect_uri: redirectUri,
          scope: "openid profile email",
          state: `state_${Date.now()}`,
          ...extraParams,
        }).toString(),
      headers: {},
    });
    expect(response.status).toBe(302);
    return new URL(response.headers.location, backendUrl).searchParams.get("id");
  };

  const externalApi = async (authId, body) =>
    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/external-api-authentication`,
      body,
    });

  const authenticationStatus = async (authId) =>
    await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/authentication-status`,
      headers: {},
    });

  /** Approves the pending CIBA transaction on the authentication device. */
  const approveOnDevice = async () => {
    const transactions = await get({
      url: `${backendUrl}/${tenantId}/v1/authentication-devices/${deviceId}/authentications?flow=ciba`,
      headers: {},
    });
    expect(transactions.status).toBe(200);
    const transactionId = transactions.data.list[0].id;

    const challenge = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authentications/${transactionId}/fido-uaf-authentication-challenge`,
      body: {},
    });
    expect(challenge.status).toBe(200);

    const authentication = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authentications/${transactionId}/fido-uaf-authentication`,
      body: {},
    });
    expect(authentication.status).toBe(200);
  };

  // The ciba-delegation use case template deliberately does NOT ship this variant. Running the
  // delegation as the first factor makes the interactor resolve a user from the delegated userinfo,
  // and a caller that is not already identified ends up as a NEW user record under a different
  // provider_id — the same person gets a different subject depending on how they signed in. The
  // case is kept as the evidence for that decision, not as a recommended configuration.
  it("delegates the whole authentication to CIBA and resolves a separate user", async () => {
    const authId = await authorize();

    const viewData = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/view-data`,
      headers: {},
    });
    expect(viewData.status).toBe(200);
    expect(viewData.data.authentication_policy.description).toBe("ciba_delegation_policy");

    // 1. Start the CIBA flow on the external authorization server. The auth_req_id is kept
    //    server-side by http_request_store, so it never reaches the sign-in screen.
    const start = await externalApi(authId, {
      interaction: "ciba_start",
      login_hint: cibaLoginHint,
    });
    expect(start.status).toBe(200);
    expect(start.data.external_status_code).toBe(200);
    expect(start.data).not.toHaveProperty("auth_req_id");

    // 2. Polling before approval surfaces the CIBA error verbatim. The sign-in screen tells
    //    authorization_pending from a real failure by this field.
    const pending = await externalApi(authId, { interaction: "ciba_poll" });
    expect(pending.status).toBe(400);
    expect(pending.data.external_status_code).toBe(400);
    expect(pending.data.error).toBe("authorization_pending");

    await approveOnDevice();

    // 3. After approval the same poll succeeds and the access token is stored server-side.
    const authorized = await externalApi(authId, { interaction: "ciba_poll" });
    expect(authorized.status).toBe(200);
    expect(authorized.data.external_status_code).toBe(200);
    expect(authorized.data).not.toHaveProperty("access_token");

    // 4. The user is resolved from the userinfo of the external authorization server.
    const userinfo = await externalApi(authId, { interaction: "userinfo" });
    expect(userinfo.status).toBe(200);
    expect(userinfo.data.user).toHaveProperty("sub");
    // Not the subject of the user that approved on the device: a first-factor delegation creates
    // its own record keyed by (provider_id, external_user_id). This is why the template requires
    // the user to be identified first.
    expect(userinfo.data.user.sub).not.toBe(serverConfig.ciba.userSub ?? "");

    const status = await authenticationStatus(authId);
    expect(status.status).toBe(200);
    expect(status.data.status).toBe("success");

    // Polling failures are recorded on the interaction. The policy deliberately has no
    // failure_conditions for it, otherwise a pending poll would fail the transaction.
    const results = status.data.interaction_results["external-api-authentication"];
    expect(results.success_count).toBe(3);
    expect(results.failure_count).toBeGreaterThanOrEqual(1);

    const authorizeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/authorize`,
      body: {},
    });
    expect(authorizeResponse.status).toBe(200);
    expect(authorizeResponse.data.redirect_uri).toContain("code=");
  });

  it("uses CIBA as a second factor after password authentication", async () => {
    // acr_values selects the two-step policy on the same client.
    const authId = await authorize({ acr_values: "urn:idp:acr:password-ciba" });

    const viewData = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/view-data`,
      headers: {},
    });
    expect(viewData.status).toBe(200);
    expect(viewData.data.authentication_policy.description).toBe("ciba_delegation_mfa_policy");

    const password = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/password-authentication`,
      body: {
        username: serverConfig.ciba.username,
        password: serverConfig.ciba.userCode,
      },
    });
    expect(password.status).toBe(200);
    const passwordUserSub = password.data.user.sub;

    const start = await externalApi(authId, {
      interaction: "ciba_start",
      login_hint: cibaLoginHint,
    });
    expect(start.status).toBe(200);

    await approveOnDevice();

    const authorized = await externalApi(authId, { interaction: "ciba_poll" });
    expect(authorized.status).toBe(200);

    // As a second factor the interactor verifies that the user approved on the device is the one
    // that authenticated with the password (identity_match_field), instead of resolving a new user.
    const userinfo = await externalApi(authId, { interaction: "userinfo" });
    expect(userinfo.status).toBe(200);
    expect(userinfo.data.user.sub).toBe(passwordUserSub);

    const status = await authenticationStatus(authId);
    expect(status.status).toBe(200);
    expect(status.data.status).toBe("success");
    expect(status.data.interaction_results["password-authentication"].success_count).toBe(1);
    expect(status.data.interaction_results["external-api-authentication"].success_count).toBe(3);

    // Each step is required by name, not by the total (#1771).
    const breakdown =
      status.data.interaction_results["external-api-authentication"].interactions;
    expect(breakdown.ciba_start.success_count).toBe(1);
    expect(breakdown.ciba_poll.success_count).toBe(1);
    expect(breakdown.userinfo.success_count).toBe(1);
  });

  /**
   * The policy used to require only `external-api-authentication.success_count >= 3`, which counts
   * every interaction of this configuration together. `ciba_start` merely *initiates* a CIBA
   * request — it does not wait for the device — and it succeeds every time it is called.
   *
   * So calling it three times satisfied the total, and the second factor completed without the user
   * ever approving anything: no `ciba_poll`, no `userinfo`, no device interaction. The policy now
   * names each step through the per-interaction breakdown (#1771).
   */
  it("does not accept repeated ciba_start in place of an approval", async () => {
    const authId = await authorize({ acr_values: "urn:idp:acr:password-ciba" });

    const password = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/password-authentication`,
      body: {
        username: serverConfig.ciba.username,
        password: serverConfig.ciba.userCode,
      },
    });
    expect(password.status).toBe(200);

    for (let attempt = 0; attempt < 3; attempt++) {
      const start = await externalApi(authId, {
        interaction: "ciba_start",
        login_hint: cibaLoginHint,
      });
      expect(start.status).toBe(200);
    }

    const status = await authenticationStatus(authId);
    expect(status.status).toBe(200);

    const typeResult = status.data.interaction_results["external-api-authentication"];
    // The total reaches 3 — which is exactly why it was not a usable condition.
    expect(typeResult.success_count).toBe(3);
    expect(typeResult.interactions.ciba_start.success_count).toBe(3);
    expect(typeResult.interactions.ciba_poll).toBeUndefined();
    expect(typeResult.interactions.userinfo).toBeUndefined();

    // Authentication is not complete, and the flow cannot be authorized.
    expect(status.data.status).toBe("in_progress");

    const authorizeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/authorize`,
      body: {},
    });
    expect(authorizeResponse.status).not.toBe(200);
  }, 90000);
});
