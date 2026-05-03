/**
 * FAPI 2.0 Security Profile Final - mTLS (tls_client_auth) compliance tests.
 *
 * 本物の mTLS 接続 (mtls.api.local.test:443) を nginx の `ssl_verify_client optional`
 * 越しに張ることで、tls_client_auth と tls_client_certificate_bound_access_tokens
 * (RFC 8705) の挙動を end-to-end で確認する。
 *
 * Triggers `AuthorizationProfile.FAPI_2_0` by requesting the `fapi-2.0` scope on the dedicated
 * fapi2-tenant (UUID `edc3e984-05b6-499f-bd05-d48e5aaea1e4`).
 *
 * @see https://openid.net/specs/fapi-security-profile-2_0.html
 * @see https://datatracker.ietf.org/doc/html/rfc8705
 */
import { describe, expect, it } from "@jest/globals";
import fs from "fs";
import path from "path";
import { faker } from "@faker-js/faker";

import { mtlsPost } from "../../lib/http/mtls";
import {
  calculateDPoPJkt,
  computeAth,
  createDPoPProof,
  generateDPoPKeyPair,
} from "../../lib/dpop";
import {
  backendUrl,
  clientSecretPostClient,
  fapi2ServerConfig as serverConfig,
  mtlBackendUrl,
} from "../testConfig";
import {
  calculateCodeChallengeWithS256,
  generateCodeVerifier,
} from "../../lib/oauth";
import {
  getAuthenticationDeviceAuthenticationTransaction,
  postAuthentication,
  postAuthenticationDeviceInteraction,
  requestBackchannelAuthentications,
  requestToken,
} from "../../api/oauthClient";
import { get } from "../../lib/http";
import { requestAuthorizations } from "../../oauth/request";

// fapi2-tenant に登録済のテストユーザー (config/examples/e2e/fapi2-tenant/user/test-user.json)
const FAPI2_TEST_USER = {
  sub: "0bfd250e-4703-4cee-bc4d-06ab2ca195ae",
  email: "fapi2.tester@example.com",
  authenticationDeviceId: "fa14bee7-6f2b-4cac-9906-77ad139966fc",
};

const fapi2TenantId = "edc3e984-05b6-499f-bd05-d48e5aaea1e4";
const certDir = path.resolve(__dirname, "../../api/cert");

const FAPI2_TLS_CLIENT_1 = {
  clientIdUuid: "b58f5dec-dedb-4ee6-a7af-b03441d0e143",
  certPath: path.join(certDir, "fapi2TlsClientAuth.pem"),
  keyPath: path.join(certDir, "fapi2TlsClientAuth.key"),
  // tlsClientAuth.json の redirect_uris に登録済の URL
  redirectUri: "https://client.example.org/callback",
  fapi20Scope: "fapi-2.0",
};

const FAPI2_TLS_CLIENT_2 = {
  clientIdUuid: "2fcc1a71-b916-4e3f-a056-8092ff3f796e",
  certPath: path.join(certDir, "fapi2TlsClientAuth2.pem"),
  keyPath: path.join(certDir, "fapi2TlsClientAuth2.key"),
};

const parEndpointMtls = `${mtlBackendUrl}/${fapi2TenantId}/v1/authorizations/push`;

const buildParBody = ({ clientId, scope, redirectUri, state, codeChallenge }) => {
  const params = new URLSearchParams();
  params.append("response_type", "code");
  params.append("client_id", clientId);
  params.append("redirect_uri", redirectUri);
  params.append("scope", scope);
  params.append("state", state);
  params.append("code_challenge", codeChallenge);
  params.append("code_challenge_method", "S256");
  return params.toString();
};

describe("FAPI 2.0 Security Profile Final - mTLS (tls_client_auth)", () => {
  describe("Happy path: PAR over mTLS", () => {
    it("MUST issue request_uri when PAR is sent with valid client certificate and fapi-2.0 scope", async () => {
      const codeVerifier = generateCodeVerifier(64);
      const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);

      const response = await mtlsPost({
        url: parEndpointMtls,
        body: buildParBody({
          clientId: FAPI2_TLS_CLIENT_1.clientIdUuid,
          scope: "openid " + FAPI2_TLS_CLIENT_1.fapi20Scope,
          redirectUri: FAPI2_TLS_CLIENT_1.redirectUri,
          state: "fapi2-mtls-happy",
          codeChallenge,
        }),
        certPath: FAPI2_TLS_CLIENT_1.certPath,
        keyPath: FAPI2_TLS_CLIENT_1.keyPath,
      });

      expect(response.status).toBe(201);
      expect(response.data.request_uri).toBeDefined();
      expect(response.data.expires_in).toBeGreaterThan(0);
    });
  });

  describe("Negative: client authentication failures", () => {
    it("MUST reject PAR when certificate subject DN does not match client registration", async () => {
      // 1st client (subject=fapi2-tls-client) を呼び出すのに 2nd client の cert を提示
      // → tls_client_auth_subject_dn 不一致で拒否されるべき。
      const codeVerifier = generateCodeVerifier(64);
      const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);

      const response = await mtlsPost({
        url: parEndpointMtls,
        body: buildParBody({
          clientId: FAPI2_TLS_CLIENT_1.clientIdUuid,
          scope: "openid " + FAPI2_TLS_CLIENT_1.fapi20Scope,
          redirectUri: FAPI2_TLS_CLIENT_1.redirectUri,
          state: "fapi2-mtls-wrong-dn",
          codeChallenge,
        }),
        certPath: FAPI2_TLS_CLIENT_2.certPath,
        keyPath: FAPI2_TLS_CLIENT_2.keyPath,
      });

      expect(response.status).toBe(400);
      expect(response.data.error).toBe("invalid_client");
    });
  });

  /**
   * mTLS と DPoP は併用可能 (RFC 8705 §3.1 + RFC 9449 §6)。
   *
   * - クライアント認証は mTLS で実施 (tls_client_auth)
   * - PAR で DPoP proof を提示すると、authorization code が dpop_jkt にバインド (RFC 9449 §10.1)
   *
   * idp-server は両方併記された場合、access token の cnf に {jkt, x5t#S256} を共存させる
   * 実装 (`AccessTokenPayloadBuilder.addConfirmation`) なので、PAR が両方受け付けることを確認する。
   */
  describe("Combined: mTLS + DPoP", () => {
    it("MUST accept PAR with both mTLS client cert and DPoP proof and bind dpop_jkt to request_uri", async () => {
      const codeVerifier = generateCodeVerifier(64);
      const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);

      const dpopKeyPair = await generateDPoPKeyPair();
      const dpopProof = await createDPoPProof({
        privateKey: dpopKeyPair.privateKey,
        publicJwk: dpopKeyPair.publicJwk,
        htm: "POST",
        htu: parEndpointMtls,
      });
      // dpop_jkt は AS が DPoP proof から自動算出するためここでは検算用に保持
      const expectedJkt = await calculateDPoPJkt(dpopKeyPair.publicJwk);
      expect(expectedJkt).toBeDefined();

      const response = await mtlsPost({
        url: parEndpointMtls,
        body: buildParBody({
          clientId: FAPI2_TLS_CLIENT_1.clientIdUuid,
          scope: "openid " + FAPI2_TLS_CLIENT_1.fapi20Scope,
          redirectUri: FAPI2_TLS_CLIENT_1.redirectUri,
          state: "fapi2-mtls-dpop",
          codeChallenge,
        }),
        headers: { DPoP: dpopProof },
        certPath: FAPI2_TLS_CLIENT_1.certPath,
        keyPath: FAPI2_TLS_CLIENT_1.keyPath,
      });

      expect(response.status).toBe(201);
      expect(response.data.request_uri).toBeDefined();
    });

    /**
     * Authorization Code Flow + mTLS + DPoP combined → introspection で cnf に {jkt, x5t#S256} 両方
     * が含まれることを確認 (RFC 8705 §3.1 + RFC 9449 §6 + AccessTokenPayloadBuilder.addConfirmation)。
     *
     * 認証は fapi2-tenant の standard authentication policy (email + sms 2FA, allow_registration=true)
     * を使い、faker で都度新規ユーザーを発行する。
     */
    it("MUST issue access token bound to both mTLS cert and DPoP key (cnf has jkt + x5t#S256) via auth code flow", async () => {
      const testUser = {
        email: faker.internet.email(),
        name: faker.person.fullName(),
        phone_number: faker.phone.number("090-####-####"),
      };

      // Step 1: PAR over mTLS + DPoP proof
      const codeVerifier = generateCodeVerifier(64);
      const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);
      const dpopKeyPair = await generateDPoPKeyPair();
      const expectedJkt = await calculateDPoPJkt(dpopKeyPair.publicJwk);

      const parDpopProof = await createDPoPProof({
        privateKey: dpopKeyPair.privateKey,
        publicJwk: dpopKeyPair.publicJwk,
        htm: "POST",
        htu: parEndpointMtls,
      });

      const parResponse = await mtlsPost({
        url: parEndpointMtls,
        body: buildParBody({
          clientId: FAPI2_TLS_CLIENT_1.clientIdUuid,
          // standard policy (openid/read/account) を当てて email+sms 2FA を回す
          scope: "openid read " + FAPI2_TLS_CLIENT_1.fapi20Scope,
          redirectUri: FAPI2_TLS_CLIENT_1.redirectUri,
          state: "fapi2-combined-authcode",
          codeChallenge,
        }),
        headers: { DPoP: parDpopProof },
        certPath: FAPI2_TLS_CLIENT_1.certPath,
        keyPath: FAPI2_TLS_CLIENT_1.keyPath,
      });
      expect(parResponse.status).toBe(201);
      const requestUri = parResponse.data.request_uri;

      // Step 2: Authorize via email (1st factor) + SMS (2nd factor) with new user registration
      const interaction = async (id, user) => {
        // ----- Email (1st factor) -----
        const emailChallenge = await postAuthentication({
          endpoint: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${id}/email-authentication-challenge`,
          id,
          body: { email: user.email, template: "authentication" },
        });
        expect(emailChallenge.status).toBe(200);

        const adminToken = await requestToken({
          endpoint: clientSecretPostClient.tokenEndpoint || `${backendUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66/v1/tokens`,
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

        // ----- SMS (2nd factor) -----
        const smsChallenge = await postAuthentication({
          endpoint: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${id}/sms-authentication-challenge`,
          id,
          body: { phone_number: user.phone_number, template: "authentication" },
        });
        expect(smsChallenge.status).toBe(200);

        // mock SMS は固定値 "123456" を返す (fapi2-tenant sms config が mocky を指している)
        const smsVerify = await postAuthentication({
          endpoint: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${id}/sms-authentication`,
          id,
          body: { verification_code: "123456" },
        });
        expect(smsVerify.status).toBe(200);
      };

      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: FAPI2_TLS_CLIENT_1.clientIdUuid,
        requestUri,
        authorizeEndpoint: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/{id}/authorize`,
        user: testUser,
        interaction,
      });
      expect(authorizationResponse.code).toBeDefined();

      // Step 3: Token endpoint over mTLS + DPoP
      const tokenDpopProof = await createDPoPProof({
        privateKey: dpopKeyPair.privateKey,
        publicJwk: dpopKeyPair.publicJwk,
        htm: "POST",
        htu: `${mtlBackendUrl}/${serverConfig.tenantId}/v1/tokens`,
      });
      const tokenBody = new URLSearchParams();
      tokenBody.append("grant_type", "authorization_code");
      tokenBody.append("code", authorizationResponse.code);
      tokenBody.append("redirect_uri", FAPI2_TLS_CLIENT_1.redirectUri);
      tokenBody.append("client_id", FAPI2_TLS_CLIENT_1.clientIdUuid);
      tokenBody.append("code_verifier", codeVerifier);

      const tokenResponse = await mtlsPost({
        url: `${mtlBackendUrl}/${serverConfig.tenantId}/v1/tokens`,
        body: tokenBody.toString(),
        headers: { DPoP: tokenDpopProof },
        certPath: FAPI2_TLS_CLIENT_1.certPath,
        keyPath: FAPI2_TLS_CLIENT_1.keyPath,
      });
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data.token_type).toBe("DPoP");
      expect(tokenResponse.data.access_token).toBeDefined();

      // Step 4: Decode access_token JWT and verify cnf has both bindings
      const [, payloadB64] = tokenResponse.data.access_token.split(".");
      const accessTokenPayload = JSON.parse(
        Buffer.from(payloadB64, "base64url").toString("utf8"),
      );
      expect(accessTokenPayload.cnf).toBeDefined();
      expect(accessTokenPayload.cnf.jkt).toBe(expectedJkt);
      expect(accessTokenPayload.cnf["x5t#S256"]).toBeDefined();

      // Step 5: Introspection over mTLS — cnf must contain both jkt and x5t#S256
      const introspectionBody = new URLSearchParams();
      introspectionBody.append("token", tokenResponse.data.access_token);
      introspectionBody.append("client_id", FAPI2_TLS_CLIENT_1.clientIdUuid);
      const introspectionResponse = await mtlsPost({
        url: `${mtlBackendUrl}/${serverConfig.tenantId}/v1/tokens/introspection`,
        body: introspectionBody.toString(),
        certPath: FAPI2_TLS_CLIENT_1.certPath,
        keyPath: FAPI2_TLS_CLIENT_1.keyPath,
      });
      console.log(JSON.stringify(introspectionResponse.data, null, 2));
      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data.active).toBe(true);
      expect(introspectionResponse.data.token_type).toBe("DPoP");
      expect(introspectionResponse.data.cnf).toBeDefined();
      expect(introspectionResponse.data.cnf.jkt).toBe(expectedJkt);
      expect(introspectionResponse.data.cnf["x5t#S256"]).toBe(
        accessTokenPayload.cnf["x5t#S256"],
      );

      // Step 6: introspection-extensions (Resource Server 向け) — RS forwarding pattern。
      // RS が Client から受け取った client_cert / dpop_proof / dpop_htm / dpop_htu を
      // すべて body parameter で AS に forward し、AS が token binding と DPoP proof を検証する。
      // DPoP HTTP header は consume されない (RS 自身の AS 認証 proof と混同しないため)。
      // x-ssl-cert は RS 自身の AS 認証用 (TLS 接続) で、テストでは便宜上 Client cert を流用。
      const introspectionExtUrl = `${mtlBackendUrl}/${serverConfig.tenantId}/v1/tokens/introspection-extensions`;
      const ath = computeAth(tokenResponse.data.access_token);
      const introspectionExtDpop = await createDPoPProof({
        privateKey: dpopKeyPair.privateKey,
        publicJwk: dpopKeyPair.publicJwk,
        htm: "POST",
        htu: introspectionExtUrl,
        ath,
      });
      const clientCertPem = fs.readFileSync(FAPI2_TLS_CLIENT_1.certPath, "utf8");
      const introspectionExtBody = new URLSearchParams();
      introspectionExtBody.append("token", tokenResponse.data.access_token);
      introspectionExtBody.append("client_id", FAPI2_TLS_CLIENT_1.clientIdUuid);
      introspectionExtBody.append("client_cert", clientCertPem);
      introspectionExtBody.append("dpop_proof", introspectionExtDpop);
      introspectionExtBody.append("dpop_htm", "POST");
      introspectionExtBody.append("dpop_htu", introspectionExtUrl);
      const introspectionExtResponse = await mtlsPost({
        url: introspectionExtUrl,
        body: introspectionExtBody.toString(),
        certPath: FAPI2_TLS_CLIENT_1.certPath,
        keyPath: FAPI2_TLS_CLIENT_1.keyPath,
      });
      console.log("INTROSPECTION-EXT status=", introspectionExtResponse.status);
      console.log("INTROSPECTION-EXT body=", JSON.stringify(introspectionExtResponse.data));
      expect(introspectionExtResponse.status).toBe(200);
      expect(introspectionExtResponse.data.active).toBe(true);
      expect(introspectionExtResponse.data.token_type).toBe("DPoP");
      expect(introspectionExtResponse.data.cnf.jkt).toBe(expectedJkt);
      expect(introspectionExtResponse.data.cnf["x5t#S256"]).toBe(
        accessTokenPayload.cnf["x5t#S256"],
      );
    });

    /**
     * CIBA + mTLS + DPoP combined → introspection で cnf に {jkt, x5t#S256} 両方含まれることを確認。
     *
     * fapi2-tenant に事前登録した {@link FAPI2_TEST_USER} と authentication-device 経由で
     * password 認証を完了させる pollフロー。
     */
    it("MUST issue access token bound to both mTLS cert and DPoP key (cnf has jkt + x5t#S256) via CIBA flow", async () => {
      const dpopKeyPair = await generateDPoPKeyPair();
      const expectedJkt = await calculateDPoPJkt(dpopKeyPair.publicJwk);

      // Step 1: backchannel authentication (mTLS)
      // FAPI 1.0 Baseline/Advance スコープ (read/write) を含めると CibaProfile.FAPI_CIBA が活性化し
      // FAPI CIBA Security Profile §5.2.2.1 で signed request object が必須になるため、
      // 本テストは plain CIBA で確認する目的で `openid + fapi-2.0` のみを要求する。
      const backchannelEndpoint = `${mtlBackendUrl}/${serverConfig.tenantId}/v1/backchannel/authentications`;
      const backchannelBody = new URLSearchParams();
      backchannelBody.append("scope", "openid " + FAPI2_TLS_CLIENT_1.fapi20Scope);
      backchannelBody.append("login_hint", `email:${FAPI2_TEST_USER.email},idp:idp-server`);
      backchannelBody.append("binding_message", "999");
      backchannelBody.append("client_id", FAPI2_TLS_CLIENT_1.clientIdUuid);

      const backchannelResponse = await mtlsPost({
        url: backchannelEndpoint,
        body: backchannelBody.toString(),
        certPath: FAPI2_TLS_CLIENT_1.certPath,
        keyPath: FAPI2_TLS_CLIENT_1.keyPath,
      });
      expect(backchannelResponse.status).toBe(200);
      const authReqId = backchannelResponse.data.auth_req_id;

      // Step 2: device interaction で password 認証
      const deviceEndpoint = `${backendUrl}/${serverConfig.tenantId}/v1/authentication-devices/{id}/authentications`;
      const txResponse = await getAuthenticationDeviceAuthenticationTransaction({
        endpoint: deviceEndpoint,
        deviceId: FAPI2_TEST_USER.authenticationDeviceId,
        params: { "attributes.auth_req_id": authReqId },
      });
      expect(txResponse.status).toBe(200);
      const tx = txResponse.data.list[0];

      const completeResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        flowType: tx.flow,
        id: tx.id,
        interactionType: "password-authentication",
        body: {
          username: FAPI2_TEST_USER.email,
          password: "successUserCode001",
        },
      });
      expect(completeResponse.status).toBe(200);

      // Step 3: Token endpoint over mTLS + DPoP (CIBA grant)
      const tokenEndpointMtls = `${mtlBackendUrl}/${serverConfig.tenantId}/v1/tokens`;
      const tokenDpopProof = await createDPoPProof({
        privateKey: dpopKeyPair.privateKey,
        publicJwk: dpopKeyPair.publicJwk,
        htm: "POST",
        htu: tokenEndpointMtls,
      });
      const tokenBody = new URLSearchParams();
      tokenBody.append("grant_type", "urn:openid:params:grant-type:ciba");
      tokenBody.append("auth_req_id", authReqId);
      tokenBody.append("client_id", FAPI2_TLS_CLIENT_1.clientIdUuid);

      const tokenResponse = await mtlsPost({
        url: tokenEndpointMtls,
        body: tokenBody.toString(),
        headers: { DPoP: tokenDpopProof },
        certPath: FAPI2_TLS_CLIENT_1.certPath,
        keyPath: FAPI2_TLS_CLIENT_1.keyPath,
      });
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data.token_type).toBe("DPoP");

      // Step 4: access_token の cnf を直接確認
      const [, payloadB64] = tokenResponse.data.access_token.split(".");
      const accessTokenPayload = JSON.parse(
        Buffer.from(payloadB64, "base64url").toString("utf8"),
      );
      expect(accessTokenPayload.cnf).toBeDefined();
      expect(accessTokenPayload.cnf.jkt).toBe(expectedJkt);
      expect(accessTokenPayload.cnf["x5t#S256"]).toBeDefined();

      // Step 5: introspection でも両方の binding が見えること
      const introspectionBody = new URLSearchParams();
      introspectionBody.append("token", tokenResponse.data.access_token);
      introspectionBody.append("client_id", FAPI2_TLS_CLIENT_1.clientIdUuid);
      const introspectionResponse = await mtlsPost({
        url: `${mtlBackendUrl}/${serverConfig.tenantId}/v1/tokens/introspection`,
        body: introspectionBody.toString(),
        certPath: FAPI2_TLS_CLIENT_1.certPath,
        keyPath: FAPI2_TLS_CLIENT_1.keyPath,
      });
      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data.active).toBe(true);
      expect(introspectionResponse.data.token_type).toBe("DPoP");
      expect(introspectionResponse.data.cnf.jkt).toBe(expectedJkt);
      expect(introspectionResponse.data.cnf["x5t#S256"]).toBe(
        accessTokenPayload.cnf["x5t#S256"],
      );

      // Step 6: introspection-extensions (Resource Server 向け) — RS forwarding pattern。
      // RS が Client から受け取った client_cert / dpop_proof / dpop_htm / dpop_htu を
      // すべて body parameter で AS に forward し、AS が token binding と DPoP proof を検証する。
      // DPoP HTTP header は consume されない (RS 自身の AS 認証 proof と混同しないため)。
      // x-ssl-cert は RS 自身の AS 認証用 (TLS 接続) で、テストでは便宜上 Client cert を流用。
      const introspectionExtUrl = `${mtlBackendUrl}/${serverConfig.tenantId}/v1/tokens/introspection-extensions`;
      const ath = computeAth(tokenResponse.data.access_token);
      const introspectionExtDpop = await createDPoPProof({
        privateKey: dpopKeyPair.privateKey,
        publicJwk: dpopKeyPair.publicJwk,
        htm: "POST",
        htu: introspectionExtUrl,
        ath,
      });
      const clientCertPem = fs.readFileSync(FAPI2_TLS_CLIENT_1.certPath, "utf8");
      const introspectionExtBody = new URLSearchParams();
      introspectionExtBody.append("token", tokenResponse.data.access_token);
      introspectionExtBody.append("client_id", FAPI2_TLS_CLIENT_1.clientIdUuid);
      introspectionExtBody.append("client_cert", clientCertPem);
      introspectionExtBody.append("dpop_proof", introspectionExtDpop);
      introspectionExtBody.append("dpop_htm", "POST");
      introspectionExtBody.append("dpop_htu", introspectionExtUrl);
      const introspectionExtResponse = await mtlsPost({
        url: introspectionExtUrl,
        body: introspectionExtBody.toString(),
        certPath: FAPI2_TLS_CLIENT_1.certPath,
        keyPath: FAPI2_TLS_CLIENT_1.keyPath,
      });
      console.log("INTROSPECTION-EXT status=", introspectionExtResponse.status);
      console.log("INTROSPECTION-EXT body=", JSON.stringify(introspectionExtResponse.data));
      expect(introspectionExtResponse.status).toBe(200);
      expect(introspectionExtResponse.data.active).toBe(true);
      expect(introspectionExtResponse.data.token_type).toBe("DPoP");
      expect(introspectionExtResponse.data.cnf.jkt).toBe(expectedJkt);
      expect(introspectionExtResponse.data.cnf["x5t#S256"]).toBe(
        accessTokenPayload.cnf["x5t#S256"],
      );
    });
  });

  /**
   * introspection-extensions の sender-constraint 検証で発生する代表的な異常系。
   *
   * 1 件 CIBA でトークン (mTLS + DPoP の二重 cnf) を取得し、その同一トークンに対して
   * `client_cert` / `dpop_proof` / `dpop_htm` / `dpop_htu` を様々な形で改竄したリクエストを送り、
   * AS が `active:false` + `error: invalid_token` を返すことを確認する。
   */
  describe("Negative: introspection-extensions sender-constraint mismatch", () => {
    let accessToken;
    let dpopKeyPair;
    let introspectionExtUrl;

    beforeAll(async () => {
      introspectionExtUrl = `${mtlBackendUrl}/${serverConfig.tenantId}/v1/tokens/introspection-extensions`;
      dpopKeyPair = await generateDPoPKeyPair();

      const backchannelEndpoint = `${mtlBackendUrl}/${serverConfig.tenantId}/v1/backchannel/authentications`;
      const backchannelBody = new URLSearchParams();
      backchannelBody.append("scope", "openid " + FAPI2_TLS_CLIENT_1.fapi20Scope);
      backchannelBody.append("login_hint", `email:${FAPI2_TEST_USER.email},idp:idp-server`);
      backchannelBody.append("binding_message", "999");
      backchannelBody.append("client_id", FAPI2_TLS_CLIENT_1.clientIdUuid);

      const backchannelResponse = await mtlsPost({
        url: backchannelEndpoint,
        body: backchannelBody.toString(),
        certPath: FAPI2_TLS_CLIENT_1.certPath,
        keyPath: FAPI2_TLS_CLIENT_1.keyPath,
      });
      expect(backchannelResponse.status).toBe(200);
      const authReqId = backchannelResponse.data.auth_req_id;

      const deviceEndpoint = `${backendUrl}/${serverConfig.tenantId}/v1/authentication-devices/{id}/authentications`;
      const txResponse = await getAuthenticationDeviceAuthenticationTransaction({
        endpoint: deviceEndpoint,
        deviceId: FAPI2_TEST_USER.authenticationDeviceId,
        params: { "attributes.auth_req_id": authReqId },
      });
      expect(txResponse.status).toBe(200);
      const tx = txResponse.data.list[0];

      const completeResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        flowType: tx.flow,
        id: tx.id,
        interactionType: "password-authentication",
        body: {
          username: FAPI2_TEST_USER.email,
          password: "successUserCode001",
        },
      });
      expect(completeResponse.status).toBe(200);

      const tokenEndpointMtls = `${mtlBackendUrl}/${serverConfig.tenantId}/v1/tokens`;
      const tokenDpopProof = await createDPoPProof({
        privateKey: dpopKeyPair.privateKey,
        publicJwk: dpopKeyPair.publicJwk,
        htm: "POST",
        htu: tokenEndpointMtls,
      });
      const tokenBody = new URLSearchParams();
      tokenBody.append("grant_type", "urn:openid:params:grant-type:ciba");
      tokenBody.append("auth_req_id", authReqId);
      tokenBody.append("client_id", FAPI2_TLS_CLIENT_1.clientIdUuid);

      const tokenResponse = await mtlsPost({
        url: tokenEndpointMtls,
        body: tokenBody.toString(),
        headers: { DPoP: tokenDpopProof },
        certPath: FAPI2_TLS_CLIENT_1.certPath,
        keyPath: FAPI2_TLS_CLIENT_1.keyPath,
      });
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data.token_type).toBe("DPoP");
      accessToken = tokenResponse.data.access_token;
    });

    const buildValidDpopProof = async () => {
      const ath = computeAth(accessToken);
      return await createDPoPProof({
        privateKey: dpopKeyPair.privateKey,
        publicJwk: dpopKeyPair.publicJwk,
        htm: "POST",
        htu: introspectionExtUrl,
        ath,
      });
    };

    it("MUST reject when client_cert body parameter is missing (mTLS-bound token)", async () => {
      const dpopProof = await buildValidDpopProof();
      const body = new URLSearchParams();
      body.append("token", accessToken);
      body.append("client_id", FAPI2_TLS_CLIENT_1.clientIdUuid);
      body.append("dpop_proof", dpopProof);
      body.append("dpop_htm", "POST");
      body.append("dpop_htu", introspectionExtUrl);
      // client_cert を意図的に省略

      const res = await mtlsPost({
        url: introspectionExtUrl,
        body: body.toString(),
        certPath: FAPI2_TLS_CLIENT_1.certPath,
        keyPath: FAPI2_TLS_CLIENT_1.keyPath,
      });
      console.log("missing-cert:", JSON.stringify(res.data));
      expect(res.status).toBe(200);
      expect(res.data.active).toBe(false);
      expect(res.data.error).toBe("invalid_token");
      expect(res.data.status_code).toBe(401);
    });

    it("MUST reject when client_cert thumbprint does not match token binding", async () => {
      const dpopProof = await buildValidDpopProof();
      // FAPI2_TLS_CLIENT_2 の cert を body の client_cert にセット (token は client 1 でバインド)
      const wrongCertPem = fs.readFileSync(FAPI2_TLS_CLIENT_2.certPath, "utf8");
      const body = new URLSearchParams();
      body.append("token", accessToken);
      body.append("client_id", FAPI2_TLS_CLIENT_1.clientIdUuid);
      body.append("client_cert", wrongCertPem);
      body.append("dpop_proof", dpopProof);
      body.append("dpop_htm", "POST");
      body.append("dpop_htu", introspectionExtUrl);

      // mTLS 接続層は引き続き client 1 (caller 認証) で行う。検証対象は body の client_cert。
      const res = await mtlsPost({
        url: introspectionExtUrl,
        body: body.toString(),
        certPath: FAPI2_TLS_CLIENT_1.certPath,
        keyPath: FAPI2_TLS_CLIENT_1.keyPath,
      });
      console.log("wrong-cert:", JSON.stringify(res.data));
      expect(res.status).toBe(200);
      expect(res.data.active).toBe(false);
      expect(res.data.error).toBe("invalid_token");
      expect(res.data.status_code).toBe(401);
    });

    it("MUST reject when dpop_proof body parameter is missing (DPoP-bound token)", async () => {
      const clientCertPem = fs.readFileSync(FAPI2_TLS_CLIENT_1.certPath, "utf8");
      const body = new URLSearchParams();
      body.append("token", accessToken);
      body.append("client_id", FAPI2_TLS_CLIENT_1.clientIdUuid);
      body.append("client_cert", clientCertPem);
      // dpop_proof を意図的に省略

      const res = await mtlsPost({
        url: introspectionExtUrl,
        body: body.toString(),
        certPath: FAPI2_TLS_CLIENT_1.certPath,
        keyPath: FAPI2_TLS_CLIENT_1.keyPath,
      });
      console.log("missing-dpop:", JSON.stringify(res.data));
      expect(res.status).toBe(200);
      expect(res.data.active).toBe(false);
      expect(res.data.error).toBe("invalid_token");
      expect(res.data.status_code).toBe(401);
    });

    it("MUST reject when dpop_proof is signed with a different key than token binding", async () => {
      const otherKeyPair = await generateDPoPKeyPair();
      const ath = computeAth(accessToken);
      const wrongDpopProof = await createDPoPProof({
        privateKey: otherKeyPair.privateKey,
        publicJwk: otherKeyPair.publicJwk,
        htm: "POST",
        htu: introspectionExtUrl,
        ath,
      });
      const clientCertPem = fs.readFileSync(FAPI2_TLS_CLIENT_1.certPath, "utf8");
      const body = new URLSearchParams();
      body.append("token", accessToken);
      body.append("client_id", FAPI2_TLS_CLIENT_1.clientIdUuid);
      body.append("client_cert", clientCertPem);
      body.append("dpop_proof", wrongDpopProof);
      body.append("dpop_htm", "POST");
      body.append("dpop_htu", introspectionExtUrl);

      const res = await mtlsPost({
        url: introspectionExtUrl,
        body: body.toString(),
        certPath: FAPI2_TLS_CLIENT_1.certPath,
        keyPath: FAPI2_TLS_CLIENT_1.keyPath,
      });
      console.log("wrong-dpop-key:", JSON.stringify(res.data));
      expect(res.status).toBe(200);
      expect(res.data.active).toBe(false);
      expect(res.data.error).toBe("invalid_token");
      expect(res.data.status_code).toBe(401);
    });

    it("MUST reject when dpop_proof htm claim does not match dpop_htm body parameter", async () => {
      const ath = computeAth(accessToken);
      // proof は htm="GET" で作るが body は dpop_htm="POST" → mismatch
      const dpopProof = await createDPoPProof({
        privateKey: dpopKeyPair.privateKey,
        publicJwk: dpopKeyPair.publicJwk,
        htm: "GET",
        htu: introspectionExtUrl,
        ath,
      });
      const clientCertPem = fs.readFileSync(FAPI2_TLS_CLIENT_1.certPath, "utf8");
      const body = new URLSearchParams();
      body.append("token", accessToken);
      body.append("client_id", FAPI2_TLS_CLIENT_1.clientIdUuid);
      body.append("client_cert", clientCertPem);
      body.append("dpop_proof", dpopProof);
      body.append("dpop_htm", "POST");
      body.append("dpop_htu", introspectionExtUrl);

      const res = await mtlsPost({
        url: introspectionExtUrl,
        body: body.toString(),
        certPath: FAPI2_TLS_CLIENT_1.certPath,
        keyPath: FAPI2_TLS_CLIENT_1.keyPath,
      });
      console.log("htm-mismatch:", JSON.stringify(res.data));
      expect(res.status).toBe(200);
      expect(res.data.active).toBe(false);
      expect(res.data.error).toBe("invalid_token");
      expect(res.data.status_code).toBe(401);
    });

    it("MUST reject when dpop_proof htu claim does not match dpop_htu body parameter", async () => {
      const ath = computeAth(accessToken);
      // proof は別 URL で作るが body の dpop_htu は本来の URL → mismatch
      const dpopProof = await createDPoPProof({
        privateKey: dpopKeyPair.privateKey,
        publicJwk: dpopKeyPair.publicJwk,
        htm: "POST",
        htu: "https://attacker.example.com/forwarded",
        ath,
      });
      const clientCertPem = fs.readFileSync(FAPI2_TLS_CLIENT_1.certPath, "utf8");
      const body = new URLSearchParams();
      body.append("token", accessToken);
      body.append("client_id", FAPI2_TLS_CLIENT_1.clientIdUuid);
      body.append("client_cert", clientCertPem);
      body.append("dpop_proof", dpopProof);
      body.append("dpop_htm", "POST");
      body.append("dpop_htu", introspectionExtUrl);

      const res = await mtlsPost({
        url: introspectionExtUrl,
        body: body.toString(),
        certPath: FAPI2_TLS_CLIENT_1.certPath,
        keyPath: FAPI2_TLS_CLIENT_1.keyPath,
      });
      console.log("htu-mismatch:", JSON.stringify(res.data));
      expect(res.status).toBe(200);
      expect(res.data.active).toBe(false);
      expect(res.data.error).toBe("invalid_token");
      expect(res.data.status_code).toBe(401);
    });

    it("MUST reject when dpop_proof ath claim does not match access token hash", async () => {
      // ath を別トークンのハッシュで作る → mismatch
      const dpopProof = await createDPoPProof({
        privateKey: dpopKeyPair.privateKey,
        publicJwk: dpopKeyPair.publicJwk,
        htm: "POST",
        htu: introspectionExtUrl,
        ath: computeAth("eyJtampered.token.value"),
      });
      const clientCertPem = fs.readFileSync(FAPI2_TLS_CLIENT_1.certPath, "utf8");
      const body = new URLSearchParams();
      body.append("token", accessToken);
      body.append("client_id", FAPI2_TLS_CLIENT_1.clientIdUuid);
      body.append("client_cert", clientCertPem);
      body.append("dpop_proof", dpopProof);
      body.append("dpop_htm", "POST");
      body.append("dpop_htu", introspectionExtUrl);

      const res = await mtlsPost({
        url: introspectionExtUrl,
        body: body.toString(),
        certPath: FAPI2_TLS_CLIENT_1.certPath,
        keyPath: FAPI2_TLS_CLIENT_1.keyPath,
      });
      console.log("ath-mismatch:", JSON.stringify(res.data));
      expect(res.status).toBe(200);
      expect(res.data.active).toBe(false);
      expect(res.data.error).toBe("invalid_token");
      expect(res.data.status_code).toBe(401);
    });
  });

  /**
   * §5.3.2.1: "shall not use refresh token rotation except in extraordinary circumstances".
   *
   * fapi2-tenant の設定 {@code rotate_refresh_token: false} により、refresh エンドポイントは
   * 同じ refresh_token をレスポンスに返却する (新規発行しない)。本テストは CIBA 経由で取得した
   * RT を refresh し、同一の RT が返却されることを確認する。
   */
  describe("§5.3.2.1: MUST NOT rotate refresh tokens", () => {
    it("MUST return the same refresh_token after refresh (no rotation)", async () => {
      const dpopKeyPair = await generateDPoPKeyPair();

      // Step 1: backchannel + device interaction (CIBA flow)
      const backchannelEndpoint = `${mtlBackendUrl}/${serverConfig.tenantId}/v1/backchannel/authentications`;
      const backchannelBody = new URLSearchParams();
      backchannelBody.append("scope", "openid offline_access " + FAPI2_TLS_CLIENT_1.fapi20Scope);
      backchannelBody.append("login_hint", `email:${FAPI2_TEST_USER.email},idp:idp-server`);
      backchannelBody.append("binding_message", "999");
      backchannelBody.append("client_id", FAPI2_TLS_CLIENT_1.clientIdUuid);

      const backchannelResponse = await mtlsPost({
        url: backchannelEndpoint,
        body: backchannelBody.toString(),
        certPath: FAPI2_TLS_CLIENT_1.certPath,
        keyPath: FAPI2_TLS_CLIENT_1.keyPath,
      });
      expect(backchannelResponse.status).toBe(200);
      const authReqId = backchannelResponse.data.auth_req_id;

      const txResponse = await getAuthenticationDeviceAuthenticationTransaction({
        endpoint: `${backendUrl}/${serverConfig.tenantId}/v1/authentication-devices/{id}/authentications`,
        deviceId: FAPI2_TEST_USER.authenticationDeviceId,
        params: { "attributes.auth_req_id": authReqId },
      });
      expect(txResponse.status).toBe(200);
      const tx = txResponse.data.list[0];
      const completeResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        flowType: tx.flow,
        id: tx.id,
        interactionType: "password-authentication",
        body: { username: FAPI2_TEST_USER.email, password: "successUserCode001" },
      });
      expect(completeResponse.status).toBe(200);

      // Step 2: Token endpoint over mTLS + DPoP
      const tokenEndpointMtls = `${mtlBackendUrl}/${serverConfig.tenantId}/v1/tokens`;
      const tokenDpopProof = await createDPoPProof({
        privateKey: dpopKeyPair.privateKey,
        publicJwk: dpopKeyPair.publicJwk,
        htm: "POST",
        htu: tokenEndpointMtls,
      });
      const tokenBody = new URLSearchParams();
      tokenBody.append("grant_type", "urn:openid:params:grant-type:ciba");
      tokenBody.append("auth_req_id", authReqId);
      tokenBody.append("client_id", FAPI2_TLS_CLIENT_1.clientIdUuid);

      const tokenResponse = await mtlsPost({
        url: tokenEndpointMtls,
        body: tokenBody.toString(),
        headers: { DPoP: tokenDpopProof },
        certPath: FAPI2_TLS_CLIENT_1.certPath,
        keyPath: FAPI2_TLS_CLIENT_1.keyPath,
      });
      console.log("token-issued:", JSON.stringify(tokenResponse.data));
      expect(tokenResponse.status).toBe(200);
      const originalRefreshToken = tokenResponse.data.refresh_token;
      // FAPI 2.0 client は offline_access を要求しているのでサーバから RT が返るはず
      expect(originalRefreshToken).toBeDefined();

      // Step 3: refresh — DPoP rotation OK だが refresh_token rotation は行われない想定
      const refreshDpopProof = await createDPoPProof({
        privateKey: dpopKeyPair.privateKey,
        publicJwk: dpopKeyPair.publicJwk,
        htm: "POST",
        htu: tokenEndpointMtls,
      });
      const refreshBody = new URLSearchParams();
      refreshBody.append("grant_type", "refresh_token");
      refreshBody.append("refresh_token", originalRefreshToken);
      refreshBody.append("client_id", FAPI2_TLS_CLIENT_1.clientIdUuid);

      const refreshResponse = await mtlsPost({
        url: tokenEndpointMtls,
        body: refreshBody.toString(),
        headers: { DPoP: refreshDpopProof },
        certPath: FAPI2_TLS_CLIENT_1.certPath,
        keyPath: FAPI2_TLS_CLIENT_1.keyPath,
      });
      console.log("refresh-result:", JSON.stringify(refreshResponse.data));
      expect(refreshResponse.status).toBe(200);
      expect(refreshResponse.data.access_token).toBeDefined();
      // RT rotation 無効: response.refresh_token は元の RT と同一であるべき
      expect(refreshResponse.data.refresh_token).toBe(originalRefreshToken);
    });
  });
});
