import { describe, expect, it } from "@jest/globals";
import { backendUrl, clientSecretPostClient, serverConfig } from "../testConfig";
import { getAuthorizations, requestToken } from "../../api/oauthClient";
import { get, postWithJson } from "../../lib/http";
import { convertNextAction } from "../../lib/util";

/**
 * Issue #1416 - self-service email change must stay bound to its own auth flow.
 *
 * The email-confirm interactors are the only ones that deliberately send a verification code to a
 * *request-supplied* address and then commit it as the user's `email` / `email_verified` (and, under
 * an EMAIL identity policy, `preferred_username` — the login identifier). Every other email
 * interactor hardens against exactly that: `EmailAuthenticationChallengeInteractor.resolveEmail`
 * ignores request input once a user is established (Issue #800 / #801 identifier switching).
 *
 * Interactors are registered globally (SPI), and both interaction endpoints are unauthenticated:
 *   POST /{tenant-id}/v1/authorizations/{id}/{interaction-type}
 *   POST /{tenant-id}/v1/authentications/{id}/{interaction-type}
 * A login transaction's `user` is established by `login_hint` alone — no client authentication at
 * all — so without a flow guard an unauthenticated caller holding a login transaction id could
 * redirect a victim's identifier to an address they control.
 *
 * The legitimate entries are token-authenticated and mint a transaction whose flow is `email-verify`
 * or `email-change`:
 *   POST /{tenant-id}/v1/me/email/verification  (scope: openid)   - code to the CURRENT address only
 *   POST /{tenant-id}/v1/me/email/change        (scope: email:change) - code to a supplied address
 * These tests assert the *other* doors stay shut.
 *
 * Severity: Critical (CWE-287 / account takeover via identifier change)
 */
describe("Issue #1416: email-confirm interactions are bound to the email-verify / email-change flows", () => {
  const victimEmail = serverConfig.ciba.username;
  const victimSub = serverConfig.ciba.sub;

  // The guard rejects any flow that is not email-verify / email-change, so a login transaction is
  // refused for both interaction types even though the login flow has a policy and a user.
  const managementAccessToken = async () => {
    const response = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      grantType: "password",
      username: serverConfig.oauth.username,
      password: serverConfig.oauth.password,
      scope: clientSecretPostClient.scope,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });
    expect(response.status).toBe(200);
    return response.data.access_token;
  };

  /** Starts a login transaction whose user is established by login_hint only (no authentication). */
  const startLoginTransaction = async () => {
    const response = await getAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      clientId: clientSecretPostClient.clientId,
      responseType: "code",
      state: `email-change-guard-${Date.now()}`,
      scope: "openid profile email",
      redirectUri: clientSecretPostClient.redirectUri,
      loginHint: victimEmail,
    });
    expect(response.status).toBe(302);
    const { params } = convertNextAction(response.headers.location);
    return params.get("id");
  };

  const readVictim = async (accessToken) => {
    const response = await get({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/users/${victimSub}`,
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(response.status).toBe(200);
    return response.data;
  };

  it("rejects email-confirm interactions posted to a login transaction via /v1/authorizations", async () => {
    const accessToken = await managementAccessToken();
    const before = await readVictim(accessToken);
    const authorizationId = await startLoginTransaction();
    const attackerEmail = `attacker-${Date.now()}@email-change-guard.example.com`;

    // Both halves of the flow are guarded, not just the challenge.
    for (const interactionType of ["email-confirm-challenge", "email-confirm"]) {
      const response = await postWithJson({
        url: `${serverConfig.authorizationIdEndpoint.replace("{id}", authorizationId)}${interactionType}`,
        body: { new_email: attackerEmail, verification_code: "000000" },
      });
      console.log(
        `[authorizations] ${interactionType}: ${response.status} ${JSON.stringify(response.data)}`
      );
      expect(response.status).toBe(400);
      expect(response.data.error_description).toContain(
        "email confirmation is not allowed for this transaction"
      );
    }

    const after = await readVictim(accessToken);
    expect(after.email).toBe(before.email);
    expect(after.email).not.toBe(attackerEmail);
    expect(after.preferred_username).toBe(before.preferred_username);
  });

  it("rejects email-confirm interactions posted to a login transaction via /v1/authentications", async () => {
    const accessToken = await managementAccessToken();
    const before = await readVictim(accessToken);
    const authorizationId = await startLoginTransaction();
    const attackerEmail = `attacker-${Date.now()}@email-change-guard.example.com`;

    // /v1/authentications is keyed by the authentication transaction id, not the authorization id.
    const transactionsResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/authentication-transactions?authorization_id=${authorizationId}`,
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(transactionsResponse.status).toBe(200);
    const transactionId = transactionsResponse.data.list[0].id;

    for (const interactionType of ["email-confirm-challenge", "email-confirm"]) {
      const response = await postWithJson({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/authentications/${transactionId}/${interactionType}`,
        body: { new_email: attackerEmail, verification_code: "000000" },
      });
      console.log(
        `[authentications] ${interactionType}: ${response.status} ${JSON.stringify(response.data)}`
      );
      expect(response.status).toBe(400);
      expect(response.data.error_description).toContain(
        "email confirmation is not allowed for this transaction"
      );
    }

    const after = await readVictim(accessToken);
    expect(after.email).toBe(before.email);
    expect(after.email).not.toBe(attackerEmail);
    expect(after.preferred_username).toBe(before.preferred_username);
  });
});
