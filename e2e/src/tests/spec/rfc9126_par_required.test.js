import { describe, expect, it } from "@jest/globals";

import { get } from "../../lib/http";
import { pushAuthorizations, requestAuthorizations } from "../../oauth/request";
import { calculateCodeChallengeWithS256, generateCodeVerifier } from "../../lib/oauth";
import {
  requireParClientSecretPostClient,
  requireParServerConfig,
} from "../testConfig";

/**
 * RFC 9126 Section 5 — `require_pushed_authorization_requests`.
 *
 * These tests use a dedicated plain OIDC tenant (require-par-tenant) whose authorization server
 * advertises `require_pushed_authorization_requests: true` and whose `fapi20_scopes` is empty, so
 * the enforcement observed here is driven by the RFC 9126 flag itself — not by the FAPI 2.0
 * profile (which mandates PAR independently via a requested fapi-2.0 scope). This is the M-6
 * follow-up: previously the flag was advertised in discovery but never enforced.
 */
describe("RFC 9126 OAuth 2.0 Pushed Authorization Requests", () => {
  describe("5. Authorization Server Metadata", () => {
    const config = requireParServerConfig;
    const client = requireParClientSecretPostClient;

    it("require_pushed_authorization_requests Boolean parameter indicating whether the authorization server accepts authorization request data only via PAR - RFC 9126 Section 5 (advertised in discovery)", async () => {
      const discovery = await get({ url: config.discoveryEndpoint });

      expect(discovery.status).toBe(200);
      expect(discovery.data.require_pushed_authorization_requests).toBe(true);
    });

    it("When require_pushed_authorization_requests is true, the authorization server accepts authorization request data only via PAR - a direct authorization request MUST be rejected - RFC 9126 Section 5", async () => {
      const codeVerifier = generateCodeVerifier(64);
      const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);

      const { error } = await requestAuthorizations({
        endpoint: config.authorizationEndpoint,
        responseType: "code",
        clientId: client.clientId,
        redirectUri: client.redirectUri,
        scope: client.scope,
        state: "state-require-par",
        codeChallenge,
        codeChallengeMethod: "S256",
      });

      // The flag (not the FAPI 2.0 profile) rejects the direct request via a redirectable error.
      expect(error.error).toBe("invalid_request");
      expect(error.error_description).toContain("Pushed Authorization Requests");
    });

    it("A request sent through the PAR endpoint is accepted and a request_uri is issued - RFC 9126 Section 2.2 / 5", async () => {
      const codeVerifier = generateCodeVerifier(64);
      const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);

      const parResponse = await pushAuthorizations({
        endpoint: config.pushedAuthorizationEndpoint,
        responseType: "code",
        state: "state-require-par",
        scope: client.scope,
        redirectUri: client.redirectUri,
        clientId: client.clientId,
        codeChallenge,
        codeChallengeMethod: "S256",
        clientSecret: client.clientSecret,
      });

      expect(parResponse.status).toBe(201);
      expect(parResponse.data).toHaveProperty("request_uri");
    });
  });
});
