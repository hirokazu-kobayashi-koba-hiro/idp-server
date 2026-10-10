import { beforeAll, describe, expect, it } from "@jest/globals";
import crypto from "crypto";
import { v4 as uuidv4 } from "uuid";
import * as jose from "jose";
import { get, post, postWithJson } from "../../../lib/http";
import {
  getAuthenticationDeviceAuthenticationTransaction,
  inspectTokenWithVerification,
  postAuthenticationDeviceInteraction,
  requestBackchannelAuthentications,
  requestToken,
} from "../../../api/oauthClient";
import { pushAuthorizations, requestAuthorizations } from "../../../oauth/request";
import { adminServerConfig, backendUrl, clientSecretPostClient, serverConfig } from "../../testConfig";
import { createJwtWithPrivateKey, generateJti } from "../../../lib/jose";
import { toEpocTime } from "../../../lib/util";
import { createDPoPProof, generateDPoPKeyPair } from "../../../lib/dpop";

/**
 * Issue #1893: a DPoP proof and a Client Attestation PoP JWT are accepted once, on every path that
 * verifies them.
 *
 * The spec ledgers (rfc9449_dpop / oauth_attestation_based_client_auth) cover the requirement once,
 * at the token endpoint (and UserInfo for DPoP). The replay detector is handed to each path
 * separately, so this file sends the same JWT twice on each of the others: a path that was not
 * given the detector would accept the second one.
 */
describe("JWT replay detection on every path (#1893)", () => {
  const computeAth = (accessToken) =>
    crypto.createHash("sha256").update(accessToken).digest().toString("base64url");

  describe("DPoP proof", () => {
    let keyPair;

    beforeAll(async () => {
      keyPair = await generateDPoPKeyPair();
    });

    const proofFor = (htu, overrides = {}) =>
      createDPoPProof({
        privateKey: keyPair.privateKey,
        publicJwk: keyPair.publicJwk,
        htm: "POST",
        htu,
        overrides,
      });

    const tokenRequest = (params, dpopProof) =>
      requestToken({
        endpoint: serverConfig.tokenEndpoint,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
        additionalHeaders: { DPoP: dpopProof },
        ...params,
      });

    const expectReplayRefused = (response) => {
      expect(response.status).toBe(400);
      expect(response.data.error).toBe("invalid_dpop_proof");
      expect(response.data.error_description).toBe("DPoP proof has already been used.");
    };

    const authorizationCode = async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: `replay-${Date.now()}`,
        scope: "openid profile email " + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });
      expect(authorizationResponse.code).toBeDefined();
      return authorizationResponse.code;
    };

    it("authorization_code: refuses the proof used for another code", async () => {
      const proof = await proofFor(serverConfig.tokenEndpoint);
      const codeGrant = (code) => ({
        grantType: "authorization_code",
        code,
        redirectUri: clientSecretPostClient.redirectUri,
      });

      expect((await tokenRequest(codeGrant(await authorizationCode()), proof)).status).toBe(200);
      expectReplayRefused(await tokenRequest(codeGrant(await authorizationCode()), proof));
    });

    it("refresh_token: refuses the proof used for the previous refresh", async () => {
      const issued = await tokenRequest(
        {
          grantType: "authorization_code",
          code: await authorizationCode(),
          redirectUri: clientSecretPostClient.redirectUri,
        },
        await proofFor(serverConfig.tokenEndpoint)
      );
      expect(issued.status).toBe(200);

      const proof = await proofFor(serverConfig.tokenEndpoint);
      const first = await tokenRequest(
        { grantType: "refresh_token", refreshToken: issued.data.refresh_token },
        proof
      );
      expect(first.status).toBe(200);
      const refreshToken = first.data.refresh_token || issued.data.refresh_token;
      expectReplayRefused(
        await tokenRequest({ grantType: "refresh_token", refreshToken }, proof)
      );
    });

    it("password: refuses the same proof the second time", async () => {
      const proof = await proofFor(serverConfig.tokenEndpoint);
      const passwordGrant = {
        grantType: "password",
        scope: clientSecretPostClient.scope,
        username: serverConfig.oauth.username,
        password: serverConfig.oauth.password,
      };

      expect((await tokenRequest(passwordGrant, proof)).status).toBe(200);
      expectReplayRefused(await tokenRequest(passwordGrant, proof));
    });

    it("CIBA: refuses the proof used for another auth_req_id", async () => {
      const completedAuthReqId = async () => {
        const ciba = serverConfig.ciba;
        const response = await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          clientId: clientSecretPostClient.clientId,
          scope: "openid profile email " + clientSecretPostClient.scope,
          bindingMessage: ciba.bindingMessage,
          userCode: ciba.userCode,
          loginHint: ciba.loginHint,
          clientSecret: clientSecretPostClient.clientSecret,
        });
        expect(response.status).toBe(200);
        const authReqId = response.data.auth_req_id;
        const transactions = await getAuthenticationDeviceAuthenticationTransaction({
          endpoint: serverConfig.authenticationDeviceEndpoint,
          deviceId: ciba.authenticationDeviceId,
          params: { "attributes.auth_req_id": authReqId },
        });
        const transaction = transactions.data.list[0];
        const completed = await postAuthenticationDeviceInteraction({
          endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
          flowType: transaction.flow,
          id: transaction.id,
          interactionType: "password-authentication",
          body: { username: ciba.username, password: ciba.userCode },
        });
        expect(completed.status).toBe(200);
        return authReqId;
      };
      const proof = await proofFor(serverConfig.tokenEndpoint);
      const cibaGrant = (authReqId) => ({
        grantType: "urn:openid:params:grant-type:ciba",
        authReqId,
      });

      expect((await tokenRequest(cibaGrant(await completedAuthReqId()), proof)).status).toBe(200);
      expectReplayRefused(await tokenRequest(cibaGrant(await completedAuthReqId()), proof));
    });

    it("PAR: refuses the same proof the second time", async () => {
      const proof = await proofFor(serverConfig.pushedAuthorizationEndpoint);
      const push = () =>
        pushAuthorizations({
          endpoint: serverConfig.pushedAuthorizationEndpoint,
          clientId: clientSecretPostClient.clientId,
          clientSecret: clientSecretPostClient.clientSecret,
          responseType: "code",
          state: `replay-par-${Date.now()}`,
          scope: "openid " + clientSecretPostClient.scope,
          redirectUri: clientSecretPostClient.redirectUri,
          additionalHeaders: { DPoP: proof },
        });

      expect((await push()).status).toBe(201);
      const replayed = await push();
      expect(replayed.status).toBe(400);
      expect(replayed.data.error).toBe("invalid_dpop_proof");
      expect(replayed.data.error_description).toBe("DPoP proof has already been used.");
    });

    it("token introspection extensions: refuses the same proof the second time", async () => {
      const issued = await tokenRequest(
        { grantType: "client_credentials", scope: clientSecretPostClient.scope },
        await proofFor(serverConfig.tokenEndpoint)
      );
      expect(issued.status).toBe(200);
      const accessToken = issued.data.access_token;
      const proof = await proofFor(serverConfig.tokenIntrospectionExtensionsEndpoint, {
        ath: computeAth(accessToken),
      });
      const introspect = () =>
        inspectTokenWithVerification({
          endpoint: serverConfig.tokenIntrospectionExtensionsEndpoint,
          token: accessToken,
          clientId: clientSecretPostClient.clientId,
          clientSecret: clientSecretPostClient.clientSecret,
          dpopProof: proof,
          dpopHtm: "POST",
          dpopHtu: serverConfig.tokenIntrospectionExtensionsEndpoint,
        });

      const first = await introspect();
      expect(first.status).toBe(200);
      expect(first.data.active).toBe(true);
      const replayed = await introspect();
      expect(replayed.status).toBe(200);
      expect(replayed.data.active).toBe(false);
      expect(replayed.data.status_code).toBe(401);
      expect(replayed.data.error).toBe("invalid_token");
      expect(replayed.data.error_description).toBe("DPoP proof has already been used.");
    });

    it("/me APIs: refuses the same proof the second time", async () => {
      const issued = await tokenRequest(
        {
          grantType: "password",
          scope: "openid identity_verification_result",
          username: serverConfig.oauth.username,
          password: serverConfig.oauth.password,
        },
        await proofFor(serverConfig.tokenEndpoint)
      );
      expect(issued.status).toBe(200);
      const accessToken = issued.data.access_token;
      const meEndpoint = `${backendUrl}/${serverConfig.tenantId}/v1/me/identity-verification/results`;
      const proof = await createDPoPProof({
        privateKey: keyPair.privateKey,
        publicJwk: keyPair.publicJwk,
        htm: "GET",
        htu: meEndpoint,
        overrides: { ath: computeAth(accessToken) },
      });
      const callMe = () =>
        get({
          url: meEndpoint,
          headers: { Authorization: `DPoP ${accessToken}`, DPoP: proof },
        });

      expect((await callMe()).status).toBe(200);
      const replayed = await callMe();
      expect(replayed.status).toBe(401);
      expect(replayed.data.error).toBe("invalid_token");
      expect(replayed.data.error_description).toBe("DPoP proof has already been used.");
      expect(replayed.headers["www-authenticate"]).toBe(
        'DPoP error="invalid_token", error_description="DPoP proof has already been used."'
      );
    });
  });

  describe("Client Attestation PoP JWT", () => {
    const ATTESTATION_HEADER = "OAuth-Client-Attestation";
    const POP_HEADER = "OAuth-Client-Attestation-PoP";
    let attesterJwk;
    let instanceJwk;
    let clientId;

    const signingJwk = async (alg, kid) => {
      const { privateKey } = await jose.generateKeyPair(alg, { extractable: true });
      return { ...(await jose.exportJWK(privateKey)), use: "sig", kid, alg };
    };
    const publicJwkOf = ({ d, ...publicJwk }) => publicJwk;

    beforeAll(async () => {
      const admin = await requestToken({
        endpoint: adminServerConfig.tokenEndpoint,
        grantType: "password",
        username: adminServerConfig.oauth.username,
        password: adminServerConfig.oauth.password,
        scope: adminServerConfig.adminClient.scope,
        clientId: adminServerConfig.adminClient.clientId,
        clientSecret: adminServerConfig.adminClient.clientSecret,
      });
      expect(admin.status).toBe(200);

      const discovery = await get({ url: serverConfig.discoveryEndpoint });
      expect(discovery.data.token_endpoint_auth_methods_supported).toContain(
        "attest_jwt_client_auth"
      );

      attesterJwk = await signingJwk("ES256", "replay-attester");
      instanceJwk = await signingJwk("ES256", "replay-instance");
      clientId = uuidv4();
      const registration = await postWithJson({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/clients`,
        headers: { Authorization: `Bearer ${admin.data.access_token}` },
        body: {
          client_id: clientId,
          client_name: "JWT Replay Detection Test Client",
          token_endpoint_auth_method: "attest_jwt_client_auth",
          extension: {
            client_attestation_trust_source: "attester_jwks",
            client_attestation_attester_jwks: JSON.stringify({ keys: [publicJwkOf(attesterJwk)] }),
          },
          grant_types: [
            "client_credentials",
            "authorization_code",
            "urn:openid:params:grant-type:ciba",
          ],
          redirect_uris: [clientSecretPostClient.redirectUri],
          response_types: ["code"],
          backchannel_token_delivery_mode: "poll",
          scope: "openid profile email account management",
          enabled: true,
        },
      });
      expect(registration.status).toBe(201);
    });

    const attestation = () =>
      createJwtWithPrivateKey({
        payload: {
          iss: "replay-attester",
          sub: clientId,
          exp: toEpocTime({ adjusted: 300 }),
          cnf: { jwk: publicJwkOf(instanceJwk) },
        },
        privateKey: attesterJwk,
        algorithm: attesterJwk.alg,
        additionalOptions: { header: { typ: "oauth-client-attestation+jwt" } },
      });
    const pop = () =>
      createJwtWithPrivateKey({
        payload: { aud: serverConfig.issuer, jti: generateJti() },
        privateKey: instanceJwk,
        algorithm: instanceJwk.alg,
        additionalOptions: { header: { typ: "oauth-client-attestation-pop+jwt" } },
      });
    const headersOf = (attestationJwt, popJwt) => ({
      [ATTESTATION_HEADER]: attestationJwt,
      [POP_HEADER]: popJwt,
    });
    const form = (url, params, headers) =>
      post({ url, body: new URLSearchParams({ client_id: clientId, ...params }).toString(), headers });

    /**
     * The client authentication failure every path answers with: 401 (RFC 6749 Section 5.2,
     * RFC 7662 Section 2.3, Issue #1891).
     */
    const expectReplayRefused = (response) => {
      expect(response.status).toBe(401);
      expect(response.data.error).toBe("invalid_client_attestation");
      expect(response.data.error_description).toBe(
        `Client authentication failed: method=attest_jwt_client_auth, client_id=${clientId}, ` +
          "reason=client attestation pop jwt has already been used"
      );
    };

    /** A fresh access token for this client, from a fresh PoP. */
    const accessToken = async () => {
      const response = await form(
        serverConfig.tokenEndpoint,
        { grant_type: "client_credentials", scope: "account" },
        headersOf(attestation(), pop())
      );
      expect(response.status).toBe(200);
      return response.data.access_token;
    };

    it("PAR: refuses the same PoP the second time", async () => {
      const headers = headersOf(attestation(), pop());
      const push = () =>
        form(
          serverConfig.pushedAuthorizationEndpoint,
          {
            response_type: "code",
            scope: "openid account",
            redirect_uri: clientSecretPostClient.redirectUri,
            state: `replay-${Date.now()}`,
          },
          headers
        );

      expect((await push()).status).toBe(201);
      expectReplayRefused(await push());
    });

    it("token introspection: refuses the same PoP the second time", async () => {
      const token = await accessToken();
      const headers = headersOf(attestation(), pop());
      const introspect = () => form(serverConfig.tokenIntrospectionEndpoint, { token }, headers);

      expect((await introspect()).status).toBe(200);
      expectReplayRefused(await introspect());
    });

    it("token introspection extensions: refuses the same PoP the second time", async () => {
      const token = await accessToken();
      const headers = headersOf(attestation(), pop());
      const introspect = () =>
        form(serverConfig.tokenIntrospectionExtensionsEndpoint, { token }, headers);

      expect((await introspect()).status).toBe(200);
      expectReplayRefused(await introspect());
    });

    it("token revocation: refuses the same PoP the second time", async () => {
      const token = await accessToken();
      const headers = headersOf(attestation(), pop());
      const revoke = () => form(serverConfig.tokenRevocationEndpoint, { token }, headers);

      expect((await revoke()).status).toBe(200);
      expectReplayRefused(await revoke());
    });

    it("CIBA backchannel authentication request: refuses the same PoP the second time", async () => {
      const ciba = serverConfig.ciba;
      const headers = headersOf(attestation(), pop());
      const request = () =>
        form(
          serverConfig.backchannelAuthenticationEndpoint,
          {
            scope: "openid profile",
            binding_message: ciba.bindingMessage,
            user_code: ciba.userCode,
            login_hint: ciba.loginHint,
          },
          headers
        );

      expect((await request()).status).toBe(200);
      expectReplayRefused(await request());
    });
  });

  describe("iat window configuration", () => {
    it("refuses a window out of range (1 to 600 seconds) when a tenant is created", async () => {
      const admin = await requestToken({
        endpoint: adminServerConfig.tokenEndpoint,
        grantType: "password",
        username: adminServerConfig.oauth.username,
        password: adminServerConfig.oauth.password,
        scope: adminServerConfig.adminClient.scope,
        clientId: adminServerConfig.adminClient.clientId,
        clientSecret: adminServerConfig.adminClient.clientSecret,
      });
      expect(admin.status).toBe(200);
      const tenantId = uuidv4();
      const onboard = (extension) =>
        postWithJson({
          url: `${backendUrl}/v1/management/onboarding`,
          headers: { Authorization: `Bearer ${admin.data.access_token}` },
          body: {
            organization: { id: uuidv4(), name: `Window Org ${Date.now()}`, description: "#1893" },
            tenant: {
              id: tenantId,
              name: `Window Tenant ${Date.now()}`,
              domain: backendUrl,
              authorization_provider: "idp-server",
            },
            authorization_server: {
              issuer: `${backendUrl}/${tenantId}`,
              authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
              token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
              jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
              scopes_supported: ["openid", "management"],
              response_types_supported: ["code"],
              response_modes_supported: ["query"],
              subject_types_supported: ["public"],
              extension,
            },
            user: {
              sub: uuidv4(),
              provider_id: "idp-server",
              email: `admin-${Date.now()}@window.example.com`,
              raw_password: `AdminPass_${Date.now()}!`,
            },
            client: {
              client_id: uuidv4(),
              client_secret: `secret-${Date.now()}`,
              redirect_uris: ["https://app.example.com/callback"],
              response_types: ["code"],
              grant_types: ["authorization_code"],
              scope: "openid management",
              token_endpoint_auth_method: "client_secret_post",
            },
          },
        });

      const zero = await onboard({ dpop_proof_acceptable_window_seconds: 0 });
      expect(zero.status).toBe(400);
      expect(zero.data.error_description).toBe(
        "dpop_proof_acceptable_window_seconds must be between 1 and 600 seconds, but was 0"
      );

      const tooLong = await onboard({ client_attestation_pop_acceptable_window_seconds: 601 });
      expect(tooLong.status).toBe(400);
      expect(tooLong.data.error_description).toBe(
        "client_attestation_pop_acceptable_window_seconds must be between 1 and 600 seconds, but was 601"
      );

      // Issue #1902: the longest a client assertion is accepted for is checked the same way.
      const assertionLifetime = await onboard({ client_assertion_max_lifetime_seconds: 0 });
      expect(assertionLifetime.status).toBe(400);
      expect(assertionLifetime.data.error_description).toBe(
        "client_assertion_max_lifetime_seconds must be between 1 and 600 seconds, but was 0"
      );
    });
  });
});

