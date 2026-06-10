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
import { faker } from "@faker-js/faker";

import {
  getAuthorizations,
  getConfiguration,
  postAuthentication,
  requestToken,
} from "../../api/oauthClient";
import { get } from "../../lib/http";
import { pushAuthorizations, requestAuthorizations } from "../../oauth/request";
import {
  backendUrl,
  clientSecretPostClient,
  fapi2ClientSecretBasicClient,
  fapi2ClientSecretJwtClient,
  fapi2ClientSecretPostClient,
  fapi2NoSenderPrivateKeyJwtClient,
  fapi2NoSenderServerConfig,
  fapi2PrivateKeyJwtClient as privateKeyJwtClient,
  fapi2PublicClient,
  fapi2ServerConfig as serverConfig,
} from "../testConfig";
import { createBasicAuthHeader } from "../../lib/util";
import {
  calculateCodeChallengeWithS256,
  generateCodeVerifier,
} from "../../lib/oauth";
import {
  createClientAssertion,
  createCustomClientAssertion,
} from "../../lib/oauth";
import {
  calculateDPoPJkt,
  computeAth,
  createDPoPProof,
  generateDPoPKeyPair,
} from "../../lib/dpop";

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

    it("MUST publish all required OIDC discovery fields (issuer, endpoints, supported lists)", async () => {
      const response = await getConfiguration({
        endpoint: serverConfig.discoveryEndpoint,
      });
      expect(response.status).toBe(200);
      const d = response.data;
      // OIDC Discovery 1.0 §3 必須フィールド
      expect(d.issuer).toBe(serverConfig.issuer);
      expect(d.authorization_endpoint).toBeDefined();
      expect(d.token_endpoint).toBeDefined();
      expect(d.jwks_uri).toBeDefined();
      expect(Array.isArray(d.response_types_supported)).toBe(true);
      expect(d.response_types_supported.length).toBeGreaterThan(0);
      expect(Array.isArray(d.subject_types_supported)).toBe(true);
      expect(Array.isArray(d.id_token_signing_alg_values_supported)).toBe(true);

      // FAPI 2.0 必須エンドポイント
      expect(d.pushed_authorization_request_endpoint).toBeDefined();
      expect(d.userinfo_endpoint).toBeDefined();
      expect(d.introspection_endpoint).toBeDefined();
      expect(d.revocation_endpoint).toBeDefined();

      // FAPI 2.0 §5.3.2.1: token endpoint auth methods には mTLS / private_key_jwt が含まれる
      expect(Array.isArray(d.token_endpoint_auth_methods_supported)).toBe(true);
      const authMethods = d.token_endpoint_auth_methods_supported;
      const hasFapiCompatibleAuth =
        authMethods.includes("tls_client_auth") ||
        authMethods.includes("self_signed_tls_client_auth") ||
        authMethods.includes("private_key_jwt");
      expect(hasFapiCompatibleAuth).toBe(true);

      // FAPI 2.0 §5.3.2.1.7: PKCE S256 が含まれる
      expect(Array.isArray(d.code_challenge_methods_supported)).toBe(true);
      expect(d.code_challenge_methods_supported).toContain("S256");

      // FAPI 2.0 §5.3.2.1: mTLS endpoint aliases (RFC 8705 §5)
      expect(d.mtls_endpoint_aliases).toBeDefined();
      expect(d.tls_client_certificate_bound_access_tokens).toBe(true);
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

  /**
   * Section 5.3.2.1: General requirements for Authorization Servers.
   *
   * <h3>カバレッジマトリクス</h3>
   *
   * <p>下記要件のうち本ファイルで E2E 検証している項目は describe ブロックとして並べる。
   * 「実装あり / E2E 未実施」と「実装なし」を区別して整理する。
   *
   * <pre>
   * ✅ E2E あり
   *   #1  Discovery metadata — "Discovery metadata" describe (FAPI 2.0 専用 3 フィールド +
   *       OIDC Discovery 1.0 §3 必須フィールド網羅)
   *   #2  password grant 拒否
   *   #3  Public client 拒否
   *   #4  sender-constrained 不可サーバ拒否 (fapi2-no-sender-tenant 経由)
   *   #5  mTLS or DPoP — fapi2_0_mtls.test.js / "Happy path: PAR + DPoP"
   *   #6a mTLS auth — fapi2_0_mtls.test.js
   *   #6b private_key_jwt happy path
   *   #6c client_secret_* 拒否
   *   #7  open redirector 防止 (未登録 redirect_uri を拒否)
   *   #8a client_assertion aud 配列拒否
   *   #8b client_assertion aud != issuer 拒否
   *   #9  refresh token rotation 無効 — fapi2_0_mtls.test.js
   *   #11 auth code lifetime ≤ 60s (config 確認 + 実時間経過後の拒否)
   *   #12 dpop_jkt binding (RFC 9449 §10.1) — rfc9449_dpop.test.js
   *   #13 client_assertion iat/nbf clock skew (+10s OK / +60s 超 reject)
   *
   * ❌ 実装なし
   *   #10 server-provided DPoP nonce (RFC 9449 §8)
   *       — RFC 9449 §4.3 Check 10 + FAPI 2.0 §5.3.2.1-2.10 上 may なオプション機能。
   *         idp-server に nonce 発行 / 検証機構が存在せず、実装→E2E 双方未対応。
   *
   * ➖ N/A (テスト対象外)
   *   #14 minimum privilege restriction — SHOULD レコメンデーションでテスト不能。
   * </pre>
   *
   * @see https://openid.net/specs/fapi-security-profile-2_0.html#section-5.3.2.1
   */
  describe("Section 5.3.2.1: General requirements", () => {

    const buildClientAssertion = (overrides = {}, omit = []) =>
      createCustomClientAssertion({
        client: privateKeyJwtClient,
        issuer: serverConfig.issuer,
        overrides,
        omit,
      });

    const parWithAssertion = async (clientAssertion, extraOpts = {}) => {
      const codeVerifier = generateCodeVerifier(64);
      const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);
      return await pushAuthorizations({
        endpoint: serverConfig.pushedAuthorizationEndpoint,
        clientId: privateKeyJwtClient.clientId,
        clientAssertion,
        clientAssertionType:
          "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
        responseType: "code",
        scope: "openid " + privateKeyJwtClient.fapi20Scope,
        redirectUri: privateKeyJwtClient.redirectUri,
        state: "fapi2-general-" + Math.random().toString(36).slice(2, 8),
        codeChallenge,
        codeChallengeMethod: "S256",
        ...extraOpts,
      });
    };

    /**
     * §5.3.2.1: "shall reject requests using the resource owner password credentials grant".
     *
     * FAPI 2.0 client は grant_types に password を含まない。token endpoint で password grant を
     * 投げるとクライアント設定違反として拒否される。
     */
    describe("MUST reject requests using the resource owner password credentials grant", () => {
      it("MUST reject password grant for FAPI 2.0 private_key_jwt client", async () => {
        const clientAssertion = buildClientAssertion();
        const response = await requestToken({
          endpoint: serverConfig.tokenEndpoint,
          grantType: "password",
          username: "any.user@example.com",
          password: "anyPassword01!",
          scope: "openid " + privateKeyJwtClient.fapi20Scope,
          clientId: privateKeyJwtClient.clientId,
          clientAssertion,
          clientAssertionType:
            "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
        });

        console.log("password-grant:", response.status, JSON.stringify(response.data));
        expect(response.status).toBe(400);
        expect(["unauthorized_client", "invalid_grant", "invalid_request"]).toContain(
          response.data.error,
        );
      });
    });

    /**
     * §5.3.2.1: "shall only accept its issuer identifier value (as defined in [RFC8414]) as a
     * string in the aud claim received in client authentication assertions".
     */
    describe("MUST only accept issuer identifier as a string in client_assertion aud", () => {
      it("MUST reject client_assertion when aud is an array", async () => {
        const clientAssertion = buildClientAssertion({
          aud: [serverConfig.issuer, "https://other.example.com"],
        });

        const response = await parWithAssertion(clientAssertion);

        console.log("aud-array:", response.status, JSON.stringify(response.data));
        expect(response.status).toBeGreaterThanOrEqual(400);
        expect(response.status).toBeLessThan(500);
        expect(["invalid_client", "invalid_request"]).toContain(
          response.data.error,
        );
      });

      it("MUST reject client_assertion when aud is the token endpoint URL (not the issuer)", async () => {
        const clientAssertion = buildClientAssertion({
          aud: serverConfig.tokenEndpoint,
        });

        const response = await parWithAssertion(clientAssertion);

        console.log("aud-non-issuer:", response.status, JSON.stringify(response.data));
        expect(response.status).toBeGreaterThanOrEqual(400);
        expect(response.status).toBeLessThan(500);
        expect(["invalid_client", "invalid_request"]).toContain(
          response.data.error,
        );
      });

      it("MUST accept client_assertion when aud is the issuer identifier as a string (positive)", async () => {
        const clientAssertion = buildClientAssertion();
        const response = await parWithAssertion(clientAssertion);

        console.log("aud-issuer:", response.status, JSON.stringify(response.data));
        expect(response.status).toBe(201);
        expect(response.data.request_uri).toBeDefined();
      });
    });

    /**
     * §5.3.2.1: "to accommodate clock offsets, shall accept JWTs with an iat or nbf timestamp
     * between 0 and 10 seconds in the future but shall reject JWTs with an iat or nbf timestamp
     * greater than 60 seconds in the future".
     *
     * idp-server: {@code JwtClockSkewValidator} で iat/nbf > now+60s を拒否する。
     */
    describe("Clock skew tolerance on client_assertion iat/nbf", () => {
      it("MUST accept client_assertion with iat 5 seconds in the future (within tolerance)", async () => {
        const nowSec = Math.floor(Date.now() / 1000);
        const clientAssertion = buildClientAssertion({ iat: nowSec + 5 });

        const response = await parWithAssertion(clientAssertion);

        console.log("iat+5s:", response.status, JSON.stringify(response.data));
        expect(response.status).toBe(201);
      });

      it("MUST accept client_assertion with iat 30 seconds in the future (within 60s window)", async () => {
        const nowSec = Math.floor(Date.now() / 1000);
        const clientAssertion = buildClientAssertion({ iat: nowSec + 30 });

        const response = await parWithAssertion(clientAssertion);

        console.log("iat+30s:", response.status, JSON.stringify(response.data));
        expect(response.status).toBe(201);
      });

      it("MUST reject client_assertion with iat 120 seconds in the future (exceeds 60s window)", async () => {
        const nowSec = Math.floor(Date.now() / 1000);
        const clientAssertion = buildClientAssertion({ iat: nowSec + 120 });

        const response = await parWithAssertion(clientAssertion);

        console.log("iat+120s:", response.status, JSON.stringify(response.data));
        expect(response.status).toBeGreaterThanOrEqual(400);
        expect(response.status).toBeLessThan(500);
      });

      it("MUST reject client_assertion with nbf 120 seconds in the future (exceeds 60s window)", async () => {
        const nowSec = Math.floor(Date.now() / 1000);
        const clientAssertion = buildClientAssertion({
          nbf: nowSec + 120,
          iat: nowSec, // 通常通り
        });

        const response = await parWithAssertion(clientAssertion);

        console.log("nbf+120s:", response.status, JSON.stringify(response.data));
        expect(response.status).toBeGreaterThanOrEqual(400);
        expect(response.status).toBeLessThan(500);
      });
    });

    /**
     * §5.3.2.1: "shall only support confidential clients as defined in [RFC6749]".
     *
     * Public client (token_endpoint_auth_method=none) は fapi2-tenant に登録自体は可能だが、
     * fapi-2.0 scope を要求した PAR は {@code FapiSecurity20Verifier#throwExceptionIfPublicClient}
     * により拒否される。
     */
    describe("MUST only support confidential clients (Public client rejection)", () => {
      it("MUST reject PAR from a Public Client (token_endpoint_auth_method=none) with fapi-2.0 scope", async () => {
        const codeVerifier = generateCodeVerifier(64);
        const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);

        const response = await pushAuthorizations({
          endpoint: serverConfig.pushedAuthorizationEndpoint,
          clientId: fapi2PublicClient.clientId,
          responseType: "code",
          scope: "openid " + fapi2PublicClient.fapi20Scope,
          redirectUri: fapi2PublicClient.redirectUri,
          state: "fapi2-public-reject",
          codeChallenge,
          codeChallengeMethod: "S256",
        });

        console.log("public-client:", response.status, JSON.stringify(response.data));
        expect(response.status).toBeGreaterThanOrEqual(400);
        expect(response.status).toBeLessThan(500);
        expect(["unauthorized_client", "invalid_client", "invalid_request"]).toContain(
          response.data.error,
        );
      });
    });

    /**
     * §5.3.2.1: "shall authenticate clients using one of the following methods: MTLS as specified
     * in Section 2 of [RFC8705], or private_key_jwt as specified in Section 9 of [OIDC]".
     *
     * client_secret_basic / post / jwt は §5.3.3.4 で禁止され、
     * {@code FapiSecurity20Verifier#throwExceptionIfClientSecretPostOrClientSecretBasicOrClientSecretJwt}
     * により拒否される。
     */
    describe("MUST only authenticate via mTLS or private_key_jwt (client_secret_* rejection)", () => {
      it("MUST reject PAR using client_secret_basic with fapi-2.0 scope", async () => {
        const codeVerifier = generateCodeVerifier(64);
        const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);

        const response = await pushAuthorizations({
          endpoint: serverConfig.pushedAuthorizationEndpoint,
          clientId: fapi2ClientSecretBasicClient.clientId,
          responseType: "code",
          scope: "openid " + fapi2ClientSecretBasicClient.fapi20Scope,
          redirectUri: fapi2ClientSecretBasicClient.redirectUri,
          state: "fapi2-secret-basic",
          codeChallenge,
          codeChallengeMethod: "S256",
          additionalHeaders: createBasicAuthHeader({
            username: fapi2ClientSecretBasicClient.clientId,
            password: fapi2ClientSecretBasicClient.clientSecret,
          }),
        });

        console.log("client_secret_basic:", response.status, JSON.stringify(response.data));
        expect(response.status).toBeGreaterThanOrEqual(400);
        expect(response.status).toBeLessThan(500);
        expect(["unauthorized_client", "invalid_client", "invalid_request"]).toContain(
          response.data.error,
        );
      });

      it("MUST reject PAR using client_secret_post with fapi-2.0 scope", async () => {
        const codeVerifier = generateCodeVerifier(64);
        const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);

        const response = await pushAuthorizations({
          endpoint: serverConfig.pushedAuthorizationEndpoint,
          clientId: fapi2ClientSecretPostClient.clientId,
          clientSecret: fapi2ClientSecretPostClient.clientSecret,
          responseType: "code",
          scope: "openid " + fapi2ClientSecretPostClient.fapi20Scope,
          redirectUri: fapi2ClientSecretPostClient.redirectUri,
          state: "fapi2-secret-post",
          codeChallenge,
          codeChallengeMethod: "S256",
        });

        console.log("client_secret_post:", response.status, JSON.stringify(response.data));
        expect(response.status).toBeGreaterThanOrEqual(400);
        expect(response.status).toBeLessThan(500);
        expect(["unauthorized_client", "invalid_client", "invalid_request"]).toContain(
          response.data.error,
        );
      });

      it("MUST reject PAR using client_secret_jwt with fapi-2.0 scope", async () => {
        const codeVerifier = generateCodeVerifier(64);
        const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);

        const clientAssertion = createClientAssertion({
          client: fapi2ClientSecretJwtClient,
          issuer: serverConfig.issuer,
        });

        const response = await pushAuthorizations({
          endpoint: serverConfig.pushedAuthorizationEndpoint,
          clientId: fapi2ClientSecretJwtClient.clientId,
          clientAssertion,
          clientAssertionType:
            "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
          responseType: "code",
          scope: "openid " + fapi2ClientSecretJwtClient.fapi20Scope,
          redirectUri: fapi2ClientSecretJwtClient.redirectUri,
          state: "fapi2-secret-jwt",
          codeChallenge,
          codeChallengeMethod: "S256",
        });

        console.log("client_secret_jwt:", response.status, JSON.stringify(response.data));
        expect(response.status).toBeGreaterThanOrEqual(400);
        expect(response.status).toBeLessThan(500);
        expect(["unauthorized_client", "invalid_client", "invalid_request"]).toContain(
          response.data.error,
        );
      });
    });

    /**
     * §5.3.2.1: "shall authenticate clients using one of the following methods: ... private_key_jwt".
     *
     * private_key_jwt + DPoP の end-to-end happy path: PAR → authorization (email + SMS 2FA) → token endpoint。
     * issued access token は cnf.jkt (DPoP-bound, RFC 9449 §6) を持つこと。
     */
    describe("MUST authenticate clients via private_key_jwt (happy path)", () => {
      it("MUST issue DPoP-bound access token end-to-end via private_key_jwt + auth code flow", async () => {
        const testUser = {
          email: faker.internet.email(),
          name: faker.person.fullName(),
          phone_number: faker.phone.number("090-####-####"),
        };

        // Step 1: PAR with private_key_jwt + DPoP
        const codeVerifier = generateCodeVerifier(64);
        const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);
        const dpopKeyPair = await generateDPoPKeyPair();
        const expectedJkt = await calculateDPoPJkt(dpopKeyPair.publicJwk);

        const parDpopProof = await createDPoPProof({
          privateKey: dpopKeyPair.privateKey,
          publicJwk: dpopKeyPair.publicJwk,
          htm: "POST",
          htu: serverConfig.pushedAuthorizationEndpoint,
        });

        const parClientAssertion = createClientAssertion({
          client: privateKeyJwtClient,
          issuer: serverConfig.issuer,
        });

        const parResponse = await pushAuthorizations({
          endpoint: serverConfig.pushedAuthorizationEndpoint,
          clientId: privateKeyJwtClient.clientId,
          clientAssertion: parClientAssertion,
          clientAssertionType:
            "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
          responseType: "code",
          // standard policy で email + SMS 2FA を回す
          scope: "openid read " + privateKeyJwtClient.fapi20Scope,
          redirectUri: privateKeyJwtClient.redirectUri,
          state: "fapi2-pkjwt-happy",
          codeChallenge,
          codeChallengeMethod: "S256",
          additionalHeaders: { DPoP: parDpopProof },
        });
        expect(parResponse.status).toBe(201);
        const requestUri = parResponse.data.request_uri;

        // Step 2: Authorization endpoint + interaction (email + SMS)
        const interaction = async (id, user) => {
          // Email (1st factor)
          const emailChallenge = await postAuthentication({
            endpoint: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${id}/email-authentication-challenge`,
            id,
            body: { email: user.email, template: "authentication" },
          });
          expect(emailChallenge.status).toBe(200);

          // 管理 API 経由で verification code を引く (test-tenant admin token を借用)
          const adminToken = await requestToken({
            endpoint: `${backendUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66/v1/tokens`,
            grantType: "password",
            username: "ito.ichiro@gmail.com",
            password: "successUserCode001",
            scope: clientSecretPostClient.scope,
            clientId: clientSecretPostClient.clientId,
            clientSecret: clientSecretPostClient.clientSecret,
          });
          expect(adminToken.status).toBe(200);
          const accessToken = adminToken.data.access_token;

          const fetchVerificationCode = async (interactionType) => {
            const txResponse = await get({
              url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/authentication-transactions?authorization_id=${id}`,
              headers: { Authorization: `Bearer ${accessToken}` },
            });
            const transactionId = txResponse.data.list[0].id;
            const interactionResponse = await get({
              url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/authentication-interactions/${transactionId}/${interactionType}`,
              headers: { Authorization: `Bearer ${accessToken}` },
            });
            return interactionResponse.data.payload.verification_code;
          };

          const emailCode = await fetchVerificationCode("email-authentication-challenge");
          const emailVerify = await postAuthentication({
            endpoint: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${id}/email-authentication`,
            id,
            body: { verification_code: emailCode },
          });
          expect(emailVerify.status).toBe(200);

          // SMS (2nd factor)
          const smsChallenge = await postAuthentication({
            endpoint: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${id}/sms-authentication-challenge`,
            id,
            body: { phone_number: user.phone_number, template: "authentication" },
          });
          expect(smsChallenge.status).toBe(200);

          // mock SMS は固定値 "123456" を返す (fapi2-tenant sms config)
          const smsVerify = await postAuthentication({
            endpoint: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${id}/sms-authentication`,
            id,
            body: { verification_code: "123456" },
          });
          expect(smsVerify.status).toBe(200);
        };

        const { authorizationResponse } = await requestAuthorizations({
          endpoint: serverConfig.authorizationEndpoint,
          clientId: privateKeyJwtClient.clientId,
          requestUri,
          authorizeEndpoint: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/{id}/authorize`,
          user: testUser,
          interaction,
        });
        expect(authorizationResponse.code).toBeDefined();

        // Step 3: Token endpoint with private_key_jwt + DPoP
        const tokenDpopProof = await createDPoPProof({
          privateKey: dpopKeyPair.privateKey,
          publicJwk: dpopKeyPair.publicJwk,
          htm: "POST",
          htu: serverConfig.tokenEndpoint,
        });
        const tokenClientAssertion = createClientAssertion({
          client: privateKeyJwtClient,
          issuer: serverConfig.issuer,
        });

        const tokenResponse = await requestToken({
          endpoint: serverConfig.tokenEndpoint,
          grantType: "authorization_code",
          code: authorizationResponse.code,
          redirectUri: privateKeyJwtClient.redirectUri,
          clientId: privateKeyJwtClient.clientId,
          clientAssertion: tokenClientAssertion,
          clientAssertionType:
            "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
          codeVerifier,
          additionalHeaders: { DPoP: tokenDpopProof },
        });

        console.log("token-pkjwt:", tokenResponse.status, JSON.stringify(tokenResponse.data));
        expect(tokenResponse.status).toBe(200);
        expect(tokenResponse.data.token_type).toBe("DPoP");
        expect(tokenResponse.data.access_token).toBeDefined();

        // Step 4: access_token は DPoP-bound (cnf.jkt) を持つ
        const [, payloadB64] = tokenResponse.data.access_token.split(".");
        const accessTokenPayload = JSON.parse(
          Buffer.from(payloadB64, "base64url").toString("utf8"),
        );
        expect(accessTokenPayload.cnf).toBeDefined();
        expect(accessTokenPayload.cnf.jkt).toBe(expectedJkt);
        // private_key_jwt なので mTLS の x5t#S256 は無い
        expect(accessTokenPayload.cnf["x5t#S256"]).toBeUndefined();
      });
    });

    /**
     * §5.3.2.1: "shall only issue sender-constrained access tokens".
     *
     * tenant 設定で mTLS バインドも DPoP もどちらも無効な状態 (fapi2-no-sender-tenant) で
     * fapi-2.0 scope の PAR を投げると {@code FapiSecurity20Verifier#throwIfNotSenderConstrainedAccessToken}
     * により invalid_request 系で拒否される。
     */
    describe("MUST only issue sender-constrained access tokens (server config validation)", () => {
      it("MUST reject PAR when neither mTLS nor DPoP is enabled at the AS", async () => {
        const codeVerifier = generateCodeVerifier(64);
        const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);
        const clientAssertion = createClientAssertion({
          client: fapi2NoSenderPrivateKeyJwtClient,
          issuer: fapi2NoSenderServerConfig.issuer,
        });

        const response = await pushAuthorizations({
          endpoint: fapi2NoSenderServerConfig.pushedAuthorizationEndpoint,
          clientId: fapi2NoSenderPrivateKeyJwtClient.clientId,
          clientAssertion,
          clientAssertionType:
            "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
          responseType: "code",
          scope: "openid " + fapi2NoSenderPrivateKeyJwtClient.fapi20Scope,
          redirectUri: fapi2NoSenderPrivateKeyJwtClient.redirectUri,
          state: "fapi2-no-sender",
          codeChallenge,
          codeChallengeMethod: "S256",
        });

        console.log("no-sender:", response.status, JSON.stringify(response.data));
        expect(response.status).toBeGreaterThanOrEqual(400);
        expect(response.status).toBeLessThan(500);
        expect(["invalid_request", "invalid_client"]).toContain(
          response.data.error,
        );
      });
    });

    /**
     * §5.3.2.1: "shall not expose open redirectors".
     *
     * PAR で未登録 redirect_uri を指定すると AS が拒否する (ClientConfiguration#isRegisteredRedirectUri
     * による厳密一致検証)。これにより攻撃者が任意の URI へリダイレクトを誘導することを防ぐ。
     */
    describe("MUST NOT expose open redirectors", () => {
      it("MUST reject PAR with unregistered redirect_uri", async () => {
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
          responseType: "code",
          scope: "openid " + privateKeyJwtClient.fapi20Scope,
          redirectUri: "https://attacker.example.com/steal-code",
          state: "fapi2-open-redirect",
          codeChallenge,
          codeChallengeMethod: "S256",
        });

        console.log("open-redirect:", response.status, JSON.stringify(response.data));
        expect(response.status).toBeGreaterThanOrEqual(400);
        expect(response.status).toBeLessThan(500);
        expect(["invalid_request", "invalid_redirect_uri"]).toContain(
          response.data.error,
        );
      });
    });

    /**
     * §5.3.2.1: auth code lifetime ≤ 60 秒。fapi2-tenant 設定で
     * {@code authorization_code_valid_duration} (秒) は 10 に設定されており、要件を満たす。
     * このブロックでは config の static check + 実時間経過後の拒否動作を確認する。
     */
    describe("Authorization code lifetime", () => {
      it("MUST reject expired authorization code (fapi2-tenant: 10s)", async () => {
        const testUser = {
          email: faker.internet.email(),
          name: faker.person.fullName(),
          phone_number: faker.phone.number("090-####-####"),
        };

        // Step 1: PAR + DPoP
        const codeVerifier = generateCodeVerifier(64);
        const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);
        const dpopKeyPair = await generateDPoPKeyPair();

        const parDpopProof = await createDPoPProof({
          privateKey: dpopKeyPair.privateKey,
          publicJwk: dpopKeyPair.publicJwk,
          htm: "POST",
          htu: serverConfig.pushedAuthorizationEndpoint,
        });

        const parClientAssertion = createClientAssertion({
          client: privateKeyJwtClient,
          issuer: serverConfig.issuer,
        });

        const parResponse = await pushAuthorizations({
          endpoint: serverConfig.pushedAuthorizationEndpoint,
          clientId: privateKeyJwtClient.clientId,
          clientAssertion: parClientAssertion,
          clientAssertionType:
            "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
          responseType: "code",
          scope: "openid read " + privateKeyJwtClient.fapi20Scope,
          redirectUri: privateKeyJwtClient.redirectUri,
          state: "fapi2-code-expiry",
          codeChallenge,
          codeChallengeMethod: "S256",
          additionalHeaders: { DPoP: parDpopProof },
        });
        expect(parResponse.status).toBe(201);
        const requestUri = parResponse.data.request_uri;

        // Step 2: Authorize via interaction (再利用ヘルパ)
        const interaction = async (id, user) => {
          const emailChallenge = await postAuthentication({
            endpoint: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${id}/email-authentication-challenge`,
            id,
            body: { email: user.email, template: "authentication" },
          });
          expect(emailChallenge.status).toBe(200);

          const adminToken = await requestToken({
            endpoint: `${backendUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66/v1/tokens`,
            grantType: "password",
            username: "ito.ichiro@gmail.com",
            password: "successUserCode001",
            scope: clientSecretPostClient.scope,
            clientId: clientSecretPostClient.clientId,
            clientSecret: clientSecretPostClient.clientSecret,
          });
          expect(adminToken.status).toBe(200);
          const accessToken = adminToken.data.access_token;

          const fetchVerificationCode = async (interactionType) => {
            const txResponse = await get({
              url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/authentication-transactions?authorization_id=${id}`,
              headers: { Authorization: `Bearer ${accessToken}` },
            });
            const transactionId = txResponse.data.list[0].id;
            const interactionResponse = await get({
              url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/authentication-interactions/${transactionId}/${interactionType}`,
              headers: { Authorization: `Bearer ${accessToken}` },
            });
            return interactionResponse.data.payload.verification_code;
          };

          const emailCode = await fetchVerificationCode("email-authentication-challenge");
          const emailVerify = await postAuthentication({
            endpoint: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${id}/email-authentication`,
            id,
            body: { verification_code: emailCode },
          });
          expect(emailVerify.status).toBe(200);

          const smsChallenge = await postAuthentication({
            endpoint: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${id}/sms-authentication-challenge`,
            id,
            body: { phone_number: user.phone_number, template: "authentication" },
          });
          expect(smsChallenge.status).toBe(200);

          const smsVerify = await postAuthentication({
            endpoint: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${id}/sms-authentication`,
            id,
            body: { verification_code: "123456" },
          });
          expect(smsVerify.status).toBe(200);
        };

        const { authorizationResponse } = await requestAuthorizations({
          endpoint: serverConfig.authorizationEndpoint,
          clientId: privateKeyJwtClient.clientId,
          requestUri,
          authorizeEndpoint: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/{id}/authorize`,
          user: testUser,
          interaction,
        });
        expect(authorizationResponse.code).toBeDefined();

        // Step 3: 11 秒 sleep — fapi2-tenant の authorization_code_valid_duration=10s を超える
        await new Promise((resolve) => setTimeout(resolve, 11000));

        // Step 4: Token exchange — expired code は invalid_grant で拒否されるべき
        const tokenDpopProof = await createDPoPProof({
          privateKey: dpopKeyPair.privateKey,
          publicJwk: dpopKeyPair.publicJwk,
          htm: "POST",
          htu: serverConfig.tokenEndpoint,
        });
        const tokenClientAssertion = createClientAssertion({
          client: privateKeyJwtClient,
          issuer: serverConfig.issuer,
        });

        const tokenResponse = await requestToken({
          endpoint: serverConfig.tokenEndpoint,
          grantType: "authorization_code",
          code: authorizationResponse.code,
          redirectUri: privateKeyJwtClient.redirectUri,
          clientId: privateKeyJwtClient.clientId,
          clientAssertion: tokenClientAssertion,
          clientAssertionType:
            "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
          codeVerifier,
          additionalHeaders: { DPoP: tokenDpopProof },
        });

        console.log("expired-code:", tokenResponse.status, JSON.stringify(tokenResponse.data));
        expect(tokenResponse.status).toBe(400);
        expect(tokenResponse.data.error).toBe("invalid_grant");
      }, 30000);
    });
  });
});
