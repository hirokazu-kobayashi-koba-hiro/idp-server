/**
 * Identity Verification API + DPoP (RFC 9449)
 *
 * 身元確認 API は ProtectedResourceApiFilter 経由で `/me` 配下の保護リソースとして
 * 提供される。本テストでは DPoP-bound アクセストークン経由で身元確認エンドポイントが
 * RFC 9449 §7 (Resource Server) の検証を満たすことを確認する。
 *
 * 対象エンドポイント:
 * - GET  /v1/me/identity-verification/results (scope: identity_verification_result)
 * - GET  /v1/me/identity-verification/applications (scope: identity_verification_application)
 */
import { describe, expect, it, beforeAll } from "@jest/globals";
import crypto from "crypto";

import { requestToken } from "../../../api/oauthClient";
import { get } from "../../../lib/http";
import { backendUrl, clientSecretPostClient, serverConfig } from "../../testConfig";
import { createDPoPProof, generateDPoPKeyPair } from "../../../lib/dpop";

const computeAth = (accessToken) => {
  const hash = crypto.createHash("sha256").update(accessToken).digest();
  return hash.toString("base64url");
};

describe("Identity Verification API + DPoP", () => {
  const idvResultsEndpoint = `${backendUrl}/${serverConfig.tenantId}/v1/me/identity-verification/results`;
  const idvApplicationsEndpoint = `${backendUrl}/${serverConfig.tenantId}/v1/me/identity-verification/applications`;
  let dpopKeyPair;

  beforeAll(async () => {
    dpopKeyPair = await generateDPoPKeyPair();
  });

  const obtainDPoPBoundToken = async (keyPair) => {
    const dpopProof = await createDPoPProof({
      privateKey: keyPair.privateKey,
      publicJwk: keyPair.publicJwk,
      htm: "POST",
      htu: serverConfig.tokenEndpoint,
    });

    const tokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      grantType: "password",
      username: serverConfig.oauth.username,
      password: serverConfig.oauth.password,
      scope: "openid identity_verification_result identity_verification_application",
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
      additionalHeaders: { DPoP: dpopProof },
    });

    expect(tokenResponse.status).toBe(200);
    expect(tokenResponse.data.token_type).toBe("DPoP");
    return tokenResponse.data.access_token;
  };

  describe("GET /identity-verification/results", () => {

    it("MUST accept request with valid DPoP proof + ath", async () => {
      const accessToken = await obtainDPoPBoundToken(dpopKeyPair);

      const dpopProof = await createDPoPProof({
        privateKey: dpopKeyPair.privateKey,
        publicJwk: dpopKeyPair.publicJwk,
        htm: "GET",
        htu: idvResultsEndpoint,
        ath: computeAth(accessToken),
      });

      const response = await get({
        url: idvResultsEndpoint,
        headers: {
          Authorization: `DPoP ${accessToken}`,
          DPoP: dpopProof,
        },
      });

      expect(response.status).not.toBe(401);
      expect(response.status).toBeLessThan(500);
    });

    it("MUST reject DPoP-bound token presented as Bearer (RFC 9449 §7.1 token type downgrade)", async () => {
      const accessToken = await obtainDPoPBoundToken(dpopKeyPair);

      const response = await get({
        url: idvResultsEndpoint,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });

      expect(response.status).toBe(401);
    });

    it("MUST reject when DPoP proof header is missing", async () => {
      const accessToken = await obtainDPoPBoundToken(dpopKeyPair);

      const response = await get({
        url: idvResultsEndpoint,
        headers: {
          Authorization: `DPoP ${accessToken}`,
        },
      });

      expect(response.status).toBe(401);
    });

    it("MUST reject when DPoP proof signed by a different key (cnf.jkt mismatch)", async () => {
      const accessToken = await obtainDPoPBoundToken(dpopKeyPair);

      const otherKeyPair = await generateDPoPKeyPair();
      const wrongKeyProof = await createDPoPProof({
        privateKey: otherKeyPair.privateKey,
        publicJwk: otherKeyPair.publicJwk,
        htm: "GET",
        htu: idvResultsEndpoint,
        ath: computeAth(accessToken),
      });

      const response = await get({
        url: idvResultsEndpoint,
        headers: {
          Authorization: `DPoP ${accessToken}`,
          DPoP: wrongKeyProof,
        },
      });

      expect(response.status).toBe(401);
    });

    it("MUST reject when DPoP proof has incorrect ath claim", async () => {
      const accessToken = await obtainDPoPBoundToken(dpopKeyPair);

      const wrongAthProof = await createDPoPProof({
        privateKey: dpopKeyPair.privateKey,
        publicJwk: dpopKeyPair.publicJwk,
        htm: "GET",
        htu: idvResultsEndpoint,
        ath: computeAth("different-access-token"),
      });

      const response = await get({
        url: idvResultsEndpoint,
        headers: {
          Authorization: `DPoP ${accessToken}`,
          DPoP: wrongAthProof,
        },
      });

      expect(response.status).toBe(401);
    });
  });

  describe("GET /identity-verification/applications", () => {

    it("MUST accept request with valid DPoP proof + ath", async () => {
      const accessToken = await obtainDPoPBoundToken(dpopKeyPair);

      const dpopProof = await createDPoPProof({
        privateKey: dpopKeyPair.privateKey,
        publicJwk: dpopKeyPair.publicJwk,
        htm: "GET",
        htu: idvApplicationsEndpoint,
        ath: computeAth(accessToken),
      });

      const response = await get({
        url: idvApplicationsEndpoint,
        headers: {
          Authorization: `DPoP ${accessToken}`,
          DPoP: dpopProof,
        },
      });

      expect(response.status).not.toBe(401);
      expect(response.status).toBeLessThan(500);
    });

    it("MUST reject DPoP-bound token presented as Bearer", async () => {
      const accessToken = await obtainDPoPBoundToken(dpopKeyPair);

      const response = await get({
        url: idvApplicationsEndpoint,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });

      expect(response.status).toBe(401);
    });
  });
});
