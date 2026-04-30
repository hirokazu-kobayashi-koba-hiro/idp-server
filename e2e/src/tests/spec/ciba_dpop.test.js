/**
 * CIBA + DPoP Integration
 *
 * RFC 9449 (DPoP) を CIBA grant (urn:openid:params:grant-type:ciba) と組み合わせて利用する場合の
 * Token endpoint / Resource Server (UserInfo) / Refresh Token Binding の動作確認。
 *
 * RFC 9449 §10 (Authorization Code Binding) は authorization_code 前提だが、
 * Token endpoint でのアクセストークン sender-constrain は grant_type 非依存。
 * CIBA 経由で発行されたトークンも auth_code 経由のものと同じ DPoP 検証を受ける。
 *
 * @see https://www.rfc-editor.org/rfc/rfc9449
 * @see https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html
 */
import { describe, expect, it, beforeAll } from "@jest/globals";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";

import {
  getAuthenticationDeviceAuthenticationTransaction,
  getUserinfo,
  postAuthenticationDeviceInteraction,
  requestBackchannelAuthentications,
  requestToken,
} from "../../api/oauthClient";
import {
  clientSecretPostClient,
  serverConfig,
} from "../testConfig";

// eslint-disable-next-line no-undef
const jose = require("jose");

const generateDPoPKeyPair = async () => {
  const { publicKey, privateKey } = await jose.generateKeyPair("ES256", {
    extractable: true,
  });
  const publicJwk = await jose.exportJWK(publicKey);
  const privateJwk = await jose.exportJWK(privateKey);
  return { publicJwk, privateJwk, privateKey };
};

const computeAth = (accessToken) => {
  const hash = crypto.createHash("sha256").update(accessToken).digest();
  return hash.toString("base64url");
};

const createDPoPProof = async ({
  privateKey,
  publicJwk,
  htm = "POST",
  htu,
  ath,
}) => {
  const payload = {
    jti: uuidv4(),
    htm,
    htu,
    iat: Math.floor(Date.now() / 1000),
  };
  if (ath) payload.ath = ath;

  const header = {
    typ: "dpop+jwt",
    alg: "ES256",
    jwk: publicJwk,
  };

  return await new jose.SignJWT(payload)
    .setProtectedHeader(header)
    .sign(privateKey);
};

const completeBackchannelAuthentication = async () => {
  const ciba = serverConfig.ciba;

  const backchannelAuthenticationResponse = await requestBackchannelAuthentications({
    endpoint: serverConfig.backchannelAuthenticationEndpoint,
    clientId: clientSecretPostClient.clientId,
    scope: "openid profile email" + (clientSecretPostClient.scope ? " " + clientSecretPostClient.scope : ""),
    bindingMessage: ciba.bindingMessage,
    userCode: ciba.userCode,
    loginHint: ciba.loginHint,
    clientSecret: clientSecretPostClient.clientSecret,
  });
  expect(backchannelAuthenticationResponse.status).toBe(200);
  const authReqId = backchannelAuthenticationResponse.data.auth_req_id;

  const authenticationTransactionResponse = await getAuthenticationDeviceAuthenticationTransaction({
    endpoint: serverConfig.authenticationDeviceEndpoint,
    deviceId: serverConfig.ciba.authenticationDeviceId,
    params: {
      "attributes.auth_req_id": authReqId,
    },
  });
  expect(authenticationTransactionResponse.status).toBe(200);

  const authenticationTransaction = authenticationTransactionResponse.data.list[0];

  const completeResponse = await postAuthenticationDeviceInteraction({
    endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
    flowType: authenticationTransaction.flow,
    id: authenticationTransaction.id,
    interactionType: "password-authentication",
    body: {
      username: serverConfig.ciba.username,
      password: serverConfig.ciba.userCode,
    },
  });
  expect(completeResponse.status).toBe(200);

  return authReqId;
};

/**
 * CIBA flow を完了させて DPoP-bound access token / refresh token を取得する。
 */
const obtainCibaDPoPBoundTokens = async (keyPair) => {
  const authReqId = await completeBackchannelAuthentication();

  const dpopProof = await createDPoPProof({
    privateKey: keyPair.privateKey,
    publicJwk: keyPair.publicJwk,
    htm: "POST",
    htu: serverConfig.tokenEndpoint,
  });

  const tokenResponse = await requestToken({
    endpoint: serverConfig.tokenEndpoint,
    grantType: "urn:openid:params:grant-type:ciba",
    authReqId,
    clientId: clientSecretPostClient.clientId,
    clientSecret: clientSecretPostClient.clientSecret,
    additionalHeaders: { DPoP: dpopProof },
  });

  expect(tokenResponse.status).toBe(200);
  expect(tokenResponse.data.token_type).toBe("DPoP");
  return tokenResponse.data;
};

describe("CIBA + RFC 9449 (DPoP)", () => {
  let dpopKeyPair;

  beforeAll(async () => {
    dpopKeyPair = await generateDPoPKeyPair();
  });

  /**
   * RFC 9449 Section 5: Token Request
   *
   * "To request a DPoP-bound access token, the client MUST include a DPoP proof
   *  in the token request via the DPoP header field."
   *
   * "The authorization server MUST validate the DPoP proof JWT presented by the client."
   *
   * Section 5.1: token_type=DPoP indicates a DPoP-bound access token.
   */
  describe("Section 5: Token Request with CIBA grant", () => {

    it("MUST issue DPoP-bound access token (token_type=DPoP) when valid DPoP proof is provided", async () => {
      const tokens = await obtainCibaDPoPBoundTokens(dpopKeyPair);
      expect(tokens.token_type).toBe("DPoP");
      expect(tokens).toHaveProperty("access_token");
      expect(tokens).toHaveProperty("refresh_token");
    });

    it("MUST issue Bearer token when DPoP proof is absent (DPoP is optional at token endpoint)", async () => {
      const authReqId = await completeBackchannelAuthentication();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "urn:openid:params:grant-type:ciba",
        authReqId,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data.token_type).toBe("Bearer");
    });

    it("MUST reject token request with malformed DPoP proof (RFC 9449 §5.10 invalid_dpop_proof)", async () => {
      const authReqId = await completeBackchannelAuthentication();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "urn:openid:params:grant-type:ciba",
        authReqId,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
        additionalHeaders: { DPoP: "not-a-valid-jwt" },
      });

      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.data.error).toBe("invalid_dpop_proof");
    });

    it("MUST reject token request when DPoP proof htu does not match the token endpoint (§4.3 Check 9)", async () => {
      const authReqId = await completeBackchannelAuthentication();

      const wrongHtuProof = await createDPoPProof({
        privateKey: dpopKeyPair.privateKey,
        publicJwk: dpopKeyPair.publicJwk,
        htm: "POST",
        htu: "https://attacker.example.com/token",
      });

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "urn:openid:params:grant-type:ciba",
        authReqId,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
        additionalHeaders: { DPoP: wrongHtuProof },
      });

      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.data.error).toBe("invalid_dpop_proof");
    });
  });

  /**
   * RFC 9449 Section 7: Protected Resource Access
   *
   * Section 7.1: When a DPoP-bound access token is presented to a protected resource,
   *   the resource server MUST verify:
   *   - the DPoP proof is valid
   *   - the public key in the DPoP proof matches the cnf.jkt of the access token
   *   - the ath claim equals the hash of the access token
   *
   * Section 7.1 also: A DPoP-bound access token MUST NOT be accepted as a Bearer token.
   */
  describe("Section 7: Resource Server (UserInfo) verification", () => {

    it("MUST accept DPoP-bound token with valid DPoP proof + ath", async () => {
      const tokens = await obtainCibaDPoPBoundTokens(dpopKeyPair);
      const accessToken = tokens.access_token;

      const userinfoProof = await createDPoPProof({
        privateKey: dpopKeyPair.privateKey,
        publicJwk: dpopKeyPair.publicJwk,
        htm: "GET",
        htu: serverConfig.userinfoEndpoint,
        ath: computeAth(accessToken),
      });

      const userinfoResponse = await getUserinfo({
        endpoint: serverConfig.userinfoEndpoint,
        authorizationHeader: {
          Authorization: `DPoP ${accessToken}`,
          DPoP: userinfoProof,
        },
      });

      expect(userinfoResponse.status).toBe(200);
      expect(userinfoResponse.data).toHaveProperty("sub");
    });

    it("MUST reject DPoP-bound token presented as Bearer (§7.1 token type downgrade)", async () => {
      const tokens = await obtainCibaDPoPBoundTokens(dpopKeyPair);
      const accessToken = tokens.access_token;

      const userinfoResponse = await getUserinfo({
        endpoint: serverConfig.userinfoEndpoint,
        authorizationHeader: {
          Authorization: `Bearer ${accessToken}`,
        },
      });

      expect(userinfoResponse.status).toBe(401);
    });

    it("MUST reject DPoP-bound token without DPoP proof header (§7.1 missing proof)", async () => {
      const tokens = await obtainCibaDPoPBoundTokens(dpopKeyPair);
      const accessToken = tokens.access_token;

      const userinfoResponse = await getUserinfo({
        endpoint: serverConfig.userinfoEndpoint,
        authorizationHeader: {
          Authorization: `DPoP ${accessToken}`,
        },
      });

      expect(userinfoResponse.status).toBe(401);
    });

    it("MUST reject DPoP-bound token when DPoP proof signed by a different key (§7.1 cnf.jkt mismatch)", async () => {
      const tokens = await obtainCibaDPoPBoundTokens(dpopKeyPair);
      const accessToken = tokens.access_token;

      const otherKeyPair = await generateDPoPKeyPair();
      const wrongKeyProof = await createDPoPProof({
        privateKey: otherKeyPair.privateKey,
        publicJwk: otherKeyPair.publicJwk,
        htm: "GET",
        htu: serverConfig.userinfoEndpoint,
        ath: computeAth(accessToken),
      });

      const userinfoResponse = await getUserinfo({
        endpoint: serverConfig.userinfoEndpoint,
        authorizationHeader: {
          Authorization: `DPoP ${accessToken}`,
          DPoP: wrongKeyProof,
        },
      });

      expect(userinfoResponse.status).toBe(401);
    });

    it("MUST reject DPoP-bound token with mismatched ath claim (§4.2 / §7.1 ath verification)", async () => {
      const tokens = await obtainCibaDPoPBoundTokens(dpopKeyPair);
      const accessToken = tokens.access_token;

      const wrongAthProof = await createDPoPProof({
        privateKey: dpopKeyPair.privateKey,
        publicJwk: dpopKeyPair.publicJwk,
        htm: "GET",
        htu: serverConfig.userinfoEndpoint,
        ath: computeAth("different-access-token-value"),
      });

      const userinfoResponse = await getUserinfo({
        endpoint: serverConfig.userinfoEndpoint,
        authorizationHeader: {
          Authorization: `DPoP ${accessToken}`,
          DPoP: wrongAthProof,
        },
      });

      expect(userinfoResponse.status).toBe(401);
    });
  });

  /**
   * RFC 9449 Section 5.8: Refresh Token Binding
   *
   * "Refresh tokens issued to public clients in the token response MUST be bound to
   *  the public key associated with the DPoP proof JWT presented at the token endpoint."
   *
   * "The client MUST present a DPoP proof signed by the same key when refreshing the token."
   */
  describe("Section 5.8: Refresh Token DPoP Key Continuity", () => {

    it("MUST accept refresh request with the same DPoP key", async () => {
      const tokens = await obtainCibaDPoPBoundTokens(dpopKeyPair);
      const refreshToken = tokens.refresh_token;

      const refreshProof = await createDPoPProof({
        privateKey: dpopKeyPair.privateKey,
        publicJwk: dpopKeyPair.publicJwk,
        htm: "POST",
        htu: serverConfig.tokenEndpoint,
      });

      const refreshResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "refresh_token",
        refreshToken,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
        additionalHeaders: { DPoP: refreshProof },
      });

      expect(refreshResponse.status).toBe(200);
      expect(refreshResponse.data.token_type).toBe("DPoP");
      expect(refreshResponse.data).toHaveProperty("access_token");
    });

    it("MUST reject refresh request with a different DPoP key (key rotation prohibited)", async () => {
      const tokens = await obtainCibaDPoPBoundTokens(dpopKeyPair);
      const refreshToken = tokens.refresh_token;

      const otherKeyPair = await generateDPoPKeyPair();
      const wrongKeyProof = await createDPoPProof({
        privateKey: otherKeyPair.privateKey,
        publicJwk: otherKeyPair.publicJwk,
        htm: "POST",
        htu: serverConfig.tokenEndpoint,
      });

      const refreshResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "refresh_token",
        refreshToken,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
        additionalHeaders: { DPoP: wrongKeyProof },
      });

      expect(refreshResponse.status).toBeGreaterThanOrEqual(400);
      expect(refreshResponse.status).toBeLessThan(500);
    });
  });
});
