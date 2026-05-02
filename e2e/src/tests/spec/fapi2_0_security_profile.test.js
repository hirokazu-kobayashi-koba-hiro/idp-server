/**
 * FAPI 2.0 Security Profile Final - Spec compliance tests
 *
 * Triggers `AuthorizationProfile.FAPI_2_0` by requesting the `fapi-2.0` scope on the test tenant
 * (configured in `config/examples/e2e/test-tenant/authorization-server/idp-server.json` via
 * `extension.fapi20_scopes: ["fapi-2.0"]`).
 *
 * @see https://openid.net/specs/fapi-security-profile-2_0.html
 */
import { describe, expect, it, beforeAll } from "@jest/globals";

import {
  getAuthorizations,
  getConfiguration,
  requestToken,
} from "../../api/oauthClient";
import { pushAuthorizations } from "../../oauth/request";
import {
  fapi2PrivateKeyJwtClient as privateKeyJwtClient,
  fapi2ServerConfig as serverConfig,
} from "../testConfig";
import {
  calculateCodeChallengeWithS256,
  generateCodeVerifier,
} from "../../lib/oauth";
import { createClientAssertion } from "../../lib/oauth";
import { createDPoPProof, generateDPoPKeyPair } from "../../lib/dpop";

describe("FAPI 2.0 Security Profile Final", () => {
  /**
   * Section 5.3.2.2.3: require_pushed_authorization_requests = true
   */
  describe("Discovery metadata", () => {
    it("MUST publish require_pushed_authorization_requests=true", async () => {
      const response = await getConfiguration({
        endpoint: serverConfig.discoveryEndpoint,
      });
      expect(response.status).toBe(200);
      expect(response.data.require_pushed_authorization_requests).toBe(true);
    });

    it("MUST publish dpop_signing_alg_values_supported", async () => {
      const response = await getConfiguration({
        endpoint: serverConfig.discoveryEndpoint,
      });
      expect(response.status).toBe(200);
      expect(Array.isArray(response.data.dpop_signing_alg_values_supported)).toBe(true);
      expect(response.data.dpop_signing_alg_values_supported.length).toBeGreaterThan(0);
    });

    it("MUST publish authorization_response_iss_parameter_supported=true (RFC 9207)", async () => {
      const response = await getConfiguration({
        endpoint: serverConfig.discoveryEndpoint,
      });
      expect(response.status).toBe(200);
      expect(response.data.authorization_response_iss_parameter_supported).toBe(true);
    });
  });

  /**
   * Section 5.3.2.2.3: PAR mandatory.
   */
  describe("Section 5.3.2.2.3: PAR mandatory", () => {
    it("MUST reject direct authorization request when fapi-2.0 scope is requested", async () => {
      const codeVerifier = generateCodeVerifier(64);
      const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);

      const response = await getAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: privateKeyJwtClient.clientId,
        responseType: "code",
        scope: "openid " + privateKeyJwtClient.fapi20Scope,
        redirectUri: privateKeyJwtClient.redirectUri,
        state: "fapi2-direct-reject",
        codeChallenge,
        codeChallengeMethod: "S256",
      });

      expect(response.status).toBeGreaterThanOrEqual(300);
      expect(response.status).toBeLessThan(500);
    });
  });

  /**
   * Section 5.3.2.1: response_type=code only (Hybrid Flow prohibited)
   */
  describe("Section 5.3.2.1: response_type=code only", () => {
    let dpopKeyPair;
    beforeAll(async () => {
      dpopKeyPair = await generateDPoPKeyPair();
    });

    it("MUST reject PAR with response_type=code id_token (Hybrid Flow)", async () => {
      const codeVerifier = generateCodeVerifier(64);
      const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);

      const clientAssertion = createClientAssertion({
        client: privateKeyJwtClient,
        issuer: serverConfig.issuer,
      });

      const response = await pushAuthorizations({
        endpoint: serverConfig.pushedAuthorizationEndpoint,
        clientId: privateKeyJwtClient.clientId,
        clientAssertion,
        clientAssertionType:
          "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
        responseType: "code id_token",
        scope: "openid " + privateKeyJwtClient.fapi20Scope,
        redirectUri: privateKeyJwtClient.redirectUri,
        state: "fapi2-hybrid",
        nonce: "fapi2-hybrid-nonce",
        codeChallenge,
        codeChallengeMethod: "S256",
      });

      expect(response.status).toBeGreaterThanOrEqual(400);
      expect(response.status).toBeLessThan(500);
    });
  });

  /**
   * Section 5.3.2.1.7: PKCE S256 mandatory
   */
  describe("Section 5.3.2.1.7: PKCE S256 mandatory", () => {
    it("MUST reject PAR without code_challenge", async () => {
      const clientAssertion = createClientAssertion({
        client: privateKeyJwtClient,
        issuer: serverConfig.issuer,
      });

      const response = await pushAuthorizations({
        endpoint: serverConfig.pushedAuthorizationEndpoint,
        clientId: privateKeyJwtClient.clientId,
        clientAssertion,
        clientAssertionType:
          "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
        responseType: "code",
        scope: "openid " + privateKeyJwtClient.fapi20Scope,
        redirectUri: privateKeyJwtClient.redirectUri,
        state: "fapi2-no-pkce",
      });

      expect(response.status).toBeGreaterThanOrEqual(400);
      expect(response.status).toBeLessThan(500);
    });
  });

  /**
   * Section 5.3.2.1: PAR + DPoP happy path
   */
  describe("Happy path: PAR + DPoP", () => {
    let dpopKeyPair;
    beforeAll(async () => {
      dpopKeyPair = await generateDPoPKeyPair();
    });

    it("MUST issue request_uri when PAR is sent with valid DPoP proof and fapi-2.0 scope", async () => {
      const codeVerifier = generateCodeVerifier(64);
      const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);

      const dpopProof = await createDPoPProof({
        privateKey: dpopKeyPair.privateKey,
        publicJwk: dpopKeyPair.publicJwk,
        htm: "POST",
        htu: serverConfig.pushedAuthorizationEndpoint,
      });

      const clientAssertion = createClientAssertion({
        client: privateKeyJwtClient,
        issuer: serverConfig.issuer,
      });

      const response = await pushAuthorizations({
        endpoint: serverConfig.pushedAuthorizationEndpoint,
        clientId: privateKeyJwtClient.clientId,
        clientAssertion,
        clientAssertionType:
          "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
        responseType: "code",
        scope: "openid " + privateKeyJwtClient.fapi20Scope,
        redirectUri: privateKeyJwtClient.redirectUri,
        state: "fapi2-happy",
        nonce: "fapi2-happy-nonce",
        codeChallenge,
        codeChallengeMethod: "S256",
        additionalHeaders: { DPoP: dpopProof },
      });

      console.log("PAR response status:", response.status);
      console.log("PAR response data:", JSON.stringify(response.data));
      expect(response.status).toBe(201);
      expect(response.data.request_uri).toBeDefined();
    });
  });
});
