/**
 * RFC 9449: OAuth 2.0 Demonstrating Proof of Possession (DPoP)
 *
 * This test suite verifies compliance with RFC 9449 for DPoP-bound access tokens.
 * DPoP binds access tokens to a client's key pair, preventing token theft and replay.
 *
 * @see https://www.rfc-editor.org/rfc/rfc9449
 */
import { describe, expect, it, beforeAll } from "@jest/globals";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";

import { requestToken, getUserinfo } from "../../api/oauthClient";
import { clientSecretPostClient, serverConfig } from "../testConfig";
import { requestAuthorizations } from "../../oauth/request";

// eslint-disable-next-line no-undef
const jose = require("jose");

/**
 * Generate an EC P-256 key pair for DPoP proof signing.
 * RFC 9449 Section 4.1: DPoP proofs MUST use asymmetric algorithms.
 */
const generateDPoPKeyPair = async () => {
  const { publicKey, privateKey } = await jose.generateKeyPair("ES256", {
    extractable: true,
  });
  const publicJwk = await jose.exportJWK(publicKey);
  const privateJwk = await jose.exportJWK(privateKey);
  return { publicJwk, privateJwk, privateKey };
};

/**
 * Create a DPoP proof JWT per RFC 9449 Section 4.2.
 *
 * @param {Object} params
 * @param {CryptoKey} params.privateKey - Private key for signing
 * @param {Object} params.publicJwk - Public JWK to include in header
 * @param {string} params.htm - HTTP method (e.g., "POST")
 * @param {string} params.htu - HTTP target URI
 * @param {Object} [params.overrides] - Override payload claims
 * @param {Object} [params.headerOverrides] - Override header fields
 * @param {string[]} [params.omitClaims] - Claims to omit from payload
 * @returns {Promise<string>} Signed DPoP proof JWT
 */
/**
 * Compute the access token hash (ath) for DPoP proof at resource endpoints.
 * RFC 9449 Section 4.2: ath = base64url(SHA-256(access_token))
 */
const computeAth = (accessToken) => {
  const hash = crypto.createHash("sha256").update(accessToken).digest();
  return hash.toString("base64url");
};

/**
 * Obtain a DPoP-bound access token via authorization_code grant (user-bound).
 * UserInfo endpoint requires a token with subject, so authorization code flow is needed.
 */
const obtainDPoPBoundTokenViaAuthCodeFlow = async (keyPair) => {
  // 1. Authorization code flow
  const { authorizationResponse } = await requestAuthorizations({
    endpoint: serverConfig.authorizationEndpoint,
    clientId: clientSecretPostClient.clientId,
    responseType: "code",
    state: "dpop-test",
    scope: "openid profile email " + clientSecretPostClient.scope,
    redirectUri: clientSecretPostClient.redirectUri,
  });

  expect(authorizationResponse.code).toBeDefined();

  // 2. Token exchange with DPoP proof
  const dpopProof = await createDPoPProof({
    privateKey: keyPair.privateKey,
    publicJwk: keyPair.publicJwk,
    htm: "POST",
    htu: serverConfig.tokenEndpoint,
  });

  const tokenResponse = await requestToken({
    endpoint: serverConfig.tokenEndpoint,
    code: authorizationResponse.code,
    grantType: "authorization_code",
    redirectUri: clientSecretPostClient.redirectUri,
    clientId: clientSecretPostClient.clientId,
    clientSecret: clientSecretPostClient.clientSecret,
    additionalHeaders: { DPoP: dpopProof },
  });

  expect(tokenResponse.status).toBe(200);
  expect(tokenResponse.data.token_type).toBe("DPoP");
  return tokenResponse.data.access_token;
};

const createDPoPProof = async ({
  privateKey,
  publicJwk,
  htm = "POST",
  htu,
  overrides = {},
  headerOverrides = {},
  omitClaims = [],
}) => {
  const payload = {
    jti: uuidv4(),
    htm,
    htu,
    iat: Math.floor(Date.now() / 1000),
    ...overrides,
  };

  // Remove specified claims
  for (const claim of omitClaims) {
    delete payload[claim];
  }

  const header = {
    typ: "dpop+jwt",
    alg: "ES256",
    jwk: publicJwk,
    ...headerOverrides,
  };

  return await new jose.SignJWT(payload)
    .setProtectedHeader(header)
    .sign(privateKey);
};

describe("RFC 9449: OAuth 2.0 Demonstrating Proof of Possession (DPoP)", () => {
  let dpopKeyPair;

  beforeAll(async () => {
    dpopKeyPair = await generateDPoPKeyPair();
  });

  /**
   * RFC 9449 Section 4: DPoP Proof JWTs
   *
   * A DPoP proof is a JWT that is signed using a private key chosen by the client.
   */
  describe("Section 4: DPoP Proof JWTs", () => {

    /**
     * RFC 9449 Section 5: Token Request with DPoP
     *
     * "To request a DPoP-bound access token, the client MUST include a DPoP
     *  proof in the token request via the DPoP header field."
     *
     * "The authorization server MUST validate the DPoP proof."
     */
    describe("5. Token Request - 'The client MUST include a DPoP proof in the token request via the DPoP header field.'", () => {

      /**
       * Test: Successful DPoP-bound token issuance with client_credentials grant
       *
       * RFC 9449 Section 5:
       * When a valid DPoP proof is provided, the authorization server issues
       * a DPoP-bound access token with token_type "DPoP".
       */
      it("MUST issue DPoP-bound access token when valid DPoP proof is provided", async () => {
        const dpopProof = await createDPoPProof({
          privateKey: dpopKeyPair.privateKey,
          publicJwk: dpopKeyPair.publicJwk,
          htm: "POST",
          htu: serverConfig.tokenEndpoint,
        });

        const tokenResponse = await requestToken({
          endpoint: serverConfig.tokenEndpoint,
          grantType: "client_credentials",
          scope: clientSecretPostClient.scope,
          clientId: clientSecretPostClient.clientId,
          clientSecret: clientSecretPostClient.clientSecret,
          additionalHeaders: { DPoP: dpopProof },
        });

        console.log(tokenResponse.data);
        expect(tokenResponse.status).toBe(200);
        expect(tokenResponse.data).toHaveProperty("access_token");
        expect(tokenResponse.data.token_type).toBe("DPoP");
        expect(tokenResponse.data).toHaveProperty("expires_in");
      });
    });

    /**
     * RFC 9449 Section 4.3: Checking DPoP Proofs
     *
     * "To validate a DPoP proof, the authorization server MUST ensure that..."
     */
    describe("4.3 Checking DPoP Proofs - Validation Requirements", () => {

      /**
       * RFC 9449 Section 4.3, Check 1:
       * "the header of the JWS [...] contains a typ field with the value dpop+jwt"
       */
      describe("4.3.1 typ header - 'the header contains a typ field with the value dpop+jwt'", () => {

        it("MUST reject DPoP proof without typ header", async () => {
          const dpopProof = await createDPoPProof({
            privateKey: dpopKeyPair.privateKey,
            publicJwk: dpopKeyPair.publicJwk,
            htm: "POST",
            htu: serverConfig.tokenEndpoint,
            headerOverrides: { typ: undefined },
          });

          const tokenResponse = await requestToken({
            endpoint: serverConfig.tokenEndpoint,
            grantType: "client_credentials",
            scope: clientSecretPostClient.scope,
            clientId: clientSecretPostClient.clientId,
            clientSecret: clientSecretPostClient.clientSecret,
            additionalHeaders: { DPoP: dpopProof },
          });

          console.log(tokenResponse.data);
          expect(tokenResponse.status).toBe(400);
          expect(tokenResponse.data.error).toBe("invalid_dpop_proof");
        });

        it("MUST reject DPoP proof with wrong typ value", async () => {
          const dpopProof = await createDPoPProof({
            privateKey: dpopKeyPair.privateKey,
            publicJwk: dpopKeyPair.publicJwk,
            htm: "POST",
            htu: serverConfig.tokenEndpoint,
            headerOverrides: { typ: "JWT" },
          });

          const tokenResponse = await requestToken({
            endpoint: serverConfig.tokenEndpoint,
            grantType: "client_credentials",
            scope: clientSecretPostClient.scope,
            clientId: clientSecretPostClient.clientId,
            clientSecret: clientSecretPostClient.clientSecret,
            additionalHeaders: { DPoP: dpopProof },
          });

          console.log(tokenResponse.data);
          expect(tokenResponse.status).toBe(400);
          expect(tokenResponse.data.error).toBe("invalid_dpop_proof");
        });
      });

      /**
       * RFC 9449 Section 4.3, Check 2:
       * "the alg field is not none and is a supported algorithm"
       */
      describe("4.3.2 alg header - 'the alg field is not none and is a supported algorithm'", () => {

        it("MUST reject DPoP proof with alg: none", async () => {
          // Manually create a JWT with alg: none (unsigned)
          const header = { typ: "dpop+jwt", alg: "none", jwk: dpopKeyPair.publicJwk };
          const payload = {
            jti: uuidv4(),
            htm: "POST",
            htu: serverConfig.tokenEndpoint,
            iat: Math.floor(Date.now() / 1000),
          };
          const encodedHeader = Buffer.from(JSON.stringify(header)).toString("base64url");
          const encodedPayload = Buffer.from(JSON.stringify(payload)).toString("base64url");
          const unsignedDpop = `${encodedHeader}.${encodedPayload}.`;

          const tokenResponse = await requestToken({
            endpoint: serverConfig.tokenEndpoint,
            grantType: "client_credentials",
            scope: clientSecretPostClient.scope,
            clientId: clientSecretPostClient.clientId,
            clientSecret: clientSecretPostClient.clientSecret,
            additionalHeaders: { DPoP: unsignedDpop },
          });

          console.log(tokenResponse.data);
          expect(tokenResponse.status).toBe(400);
          expect(tokenResponse.data.error).toBe("invalid_dpop_proof");
        });
      });

      /**
       * RFC 9449 Section 4.3, Check 3:
       * "the header contains a jwk field with the public key"
       */
      describe("4.3.3 jwk header - 'the header contains a jwk field with the public key'", () => {

        it("MUST reject DPoP proof without jwk in header", async () => {
          const dpopProof = await createDPoPProof({
            privateKey: dpopKeyPair.privateKey,
            publicJwk: dpopKeyPair.publicJwk,
            htm: "POST",
            htu: serverConfig.tokenEndpoint,
            headerOverrides: { jwk: undefined },
          });

          const tokenResponse = await requestToken({
            endpoint: serverConfig.tokenEndpoint,
            grantType: "client_credentials",
            scope: clientSecretPostClient.scope,
            clientId: clientSecretPostClient.clientId,
            clientSecret: clientSecretPostClient.clientSecret,
            additionalHeaders: { DPoP: dpopProof },
          });

          console.log(tokenResponse.data);
          expect(tokenResponse.status).toBe(400);
          expect(tokenResponse.data.error).toBe("invalid_dpop_proof");
        });

        /**
         * RFC 9449 Section 4.3, Check 4:
         * "the JWK in the header does not contain a private key"
         */
        it("MUST reject DPoP proof with private key in jwk header", async () => {
          const dpopProof = await createDPoPProof({
            privateKey: dpopKeyPair.privateKey,
            publicJwk: dpopKeyPair.publicJwk,
            htm: "POST",
            htu: serverConfig.tokenEndpoint,
            headerOverrides: { jwk: dpopKeyPair.privateJwk },
          });

          const tokenResponse = await requestToken({
            endpoint: serverConfig.tokenEndpoint,
            grantType: "client_credentials",
            scope: clientSecretPostClient.scope,
            clientId: clientSecretPostClient.clientId,
            clientSecret: clientSecretPostClient.clientSecret,
            additionalHeaders: { DPoP: dpopProof },
          });

          console.log(tokenResponse.data);
          expect(tokenResponse.status).toBe(400);
          expect(tokenResponse.data.error).toBe("invalid_dpop_proof");
        });
      });

      /**
       * RFC 9449 Section 4.3, Check 6-8:
       * Required payload claims: jti, htm, htu, iat
       */
      describe("4.3.4 Required payload claims - 'the payload contains required claims jti, htm, htu, iat'", () => {

        it("MUST reject DPoP proof without jti claim", async () => {
          const dpopProof = await createDPoPProof({
            privateKey: dpopKeyPair.privateKey,
            publicJwk: dpopKeyPair.publicJwk,
            htm: "POST",
            htu: serverConfig.tokenEndpoint,
            omitClaims: ["jti"],
          });

          const tokenResponse = await requestToken({
            endpoint: serverConfig.tokenEndpoint,
            grantType: "client_credentials",
            scope: clientSecretPostClient.scope,
            clientId: clientSecretPostClient.clientId,
            clientSecret: clientSecretPostClient.clientSecret,
            additionalHeaders: { DPoP: dpopProof },
          });

          console.log(tokenResponse.data);
          expect(tokenResponse.status).toBe(400);
          expect(tokenResponse.data.error).toBe("invalid_dpop_proof");
        });

        it("MUST reject DPoP proof without htm claim", async () => {
          const dpopProof = await createDPoPProof({
            privateKey: dpopKeyPair.privateKey,
            publicJwk: dpopKeyPair.publicJwk,
            htm: "POST",
            htu: serverConfig.tokenEndpoint,
            omitClaims: ["htm"],
          });

          const tokenResponse = await requestToken({
            endpoint: serverConfig.tokenEndpoint,
            grantType: "client_credentials",
            scope: clientSecretPostClient.scope,
            clientId: clientSecretPostClient.clientId,
            clientSecret: clientSecretPostClient.clientSecret,
            additionalHeaders: { DPoP: dpopProof },
          });

          console.log(tokenResponse.data);
          expect(tokenResponse.status).toBe(400);
          expect(tokenResponse.data.error).toBe("invalid_dpop_proof");
        });

        it("MUST reject DPoP proof without htu claim", async () => {
          const dpopProof = await createDPoPProof({
            privateKey: dpopKeyPair.privateKey,
            publicJwk: dpopKeyPair.publicJwk,
            htm: "POST",
            htu: serverConfig.tokenEndpoint,
            omitClaims: ["htu"],
          });

          const tokenResponse = await requestToken({
            endpoint: serverConfig.tokenEndpoint,
            grantType: "client_credentials",
            scope: clientSecretPostClient.scope,
            clientId: clientSecretPostClient.clientId,
            clientSecret: clientSecretPostClient.clientSecret,
            additionalHeaders: { DPoP: dpopProof },
          });

          console.log(tokenResponse.data);
          expect(tokenResponse.status).toBe(400);
          expect(tokenResponse.data.error).toBe("invalid_dpop_proof");
        });

        it("MUST reject DPoP proof without iat claim", async () => {
          const dpopProof = await createDPoPProof({
            privateKey: dpopKeyPair.privateKey,
            publicJwk: dpopKeyPair.publicJwk,
            htm: "POST",
            htu: serverConfig.tokenEndpoint,
            omitClaims: ["iat"],
          });

          const tokenResponse = await requestToken({
            endpoint: serverConfig.tokenEndpoint,
            grantType: "client_credentials",
            scope: clientSecretPostClient.scope,
            clientId: clientSecretPostClient.clientId,
            clientSecret: clientSecretPostClient.clientSecret,
            additionalHeaders: { DPoP: dpopProof },
          });

          console.log(tokenResponse.data);
          expect(tokenResponse.status).toBe(400);
          expect(tokenResponse.data.error).toBe("invalid_dpop_proof");
        });
      });

      /**
       * RFC 9449 Section 4.3, Check 8:
       * "the htm claim matches the HTTP method of the current request"
       */
      describe("4.3.5 htm matching - 'the htm claim matches the HTTP method of the current request'", () => {

        it("MUST reject DPoP proof with htm not matching request method", async () => {
          const dpopProof = await createDPoPProof({
            privateKey: dpopKeyPair.privateKey,
            publicJwk: dpopKeyPair.publicJwk,
            htm: "GET",  // Token endpoint uses POST
            htu: serverConfig.tokenEndpoint,
          });

          const tokenResponse = await requestToken({
            endpoint: serverConfig.tokenEndpoint,
            grantType: "client_credentials",
            scope: clientSecretPostClient.scope,
            clientId: clientSecretPostClient.clientId,
            clientSecret: clientSecretPostClient.clientSecret,
            additionalHeaders: { DPoP: dpopProof },
          });

          console.log(tokenResponse.data);
          expect(tokenResponse.status).toBe(400);
          expect(tokenResponse.data.error).toBe("invalid_dpop_proof");
        });
      });

      /**
       * RFC 9449 Section 4.3, Check 9:
       * "the htu claim matches the HTTP URI value for the HTTP request"
       */
      describe("4.3.6 htu matching - 'the htu claim matches the HTTP URI value for the HTTP request'", () => {

        it("MUST reject DPoP proof with htu not matching request URI", async () => {
          const dpopProof = await createDPoPProof({
            privateKey: dpopKeyPair.privateKey,
            publicJwk: dpopKeyPair.publicJwk,
            htm: "POST",
            htu: "https://wrong-server.example.com/tokens",
          });

          const tokenResponse = await requestToken({
            endpoint: serverConfig.tokenEndpoint,
            grantType: "client_credentials",
            scope: clientSecretPostClient.scope,
            clientId: clientSecretPostClient.clientId,
            clientSecret: clientSecretPostClient.clientSecret,
            additionalHeaders: { DPoP: dpopProof },
          });

          console.log(tokenResponse.data);
          expect(tokenResponse.status).toBe(400);
          expect(tokenResponse.data.error).toBe("invalid_dpop_proof");
        });
      });

      /**
       * RFC 9449 Section 4.3, Check 11:
       * "the iat value is within an acceptable window"
       */
      describe("4.3.7 iat time window - 'the iat value is within an acceptable window'", () => {

        it("MUST reject DPoP proof with iat too far in the past", async () => {
          const dpopProof = await createDPoPProof({
            privateKey: dpopKeyPair.privateKey,
            publicJwk: dpopKeyPair.publicJwk,
            htm: "POST",
            htu: serverConfig.tokenEndpoint,
            overrides: { iat: Math.floor(Date.now() / 1000) - 600 }, // 10 minutes ago
          });

          const tokenResponse = await requestToken({
            endpoint: serverConfig.tokenEndpoint,
            grantType: "client_credentials",
            scope: clientSecretPostClient.scope,
            clientId: clientSecretPostClient.clientId,
            clientSecret: clientSecretPostClient.clientSecret,
            additionalHeaders: { DPoP: dpopProof },
          });

          console.log(tokenResponse.data);
          expect(tokenResponse.status).toBe(400);
          expect(tokenResponse.data.error).toBe("invalid_dpop_proof");
        });

        it("MUST reject DPoP proof with iat too far in the future", async () => {
          const dpopProof = await createDPoPProof({
            privateKey: dpopKeyPair.privateKey,
            publicJwk: dpopKeyPair.publicJwk,
            htm: "POST",
            htu: serverConfig.tokenEndpoint,
            overrides: { iat: Math.floor(Date.now() / 1000) + 600 }, // 10 minutes ahead
          });

          const tokenResponse = await requestToken({
            endpoint: serverConfig.tokenEndpoint,
            grantType: "client_credentials",
            scope: clientSecretPostClient.scope,
            clientId: clientSecretPostClient.clientId,
            clientSecret: clientSecretPostClient.clientSecret,
            additionalHeaders: { DPoP: dpopProof },
          });

          console.log(tokenResponse.data);
          expect(tokenResponse.status).toBe(400);
          expect(tokenResponse.data.error).toBe("invalid_dpop_proof");
        });
      });

      /**
       * RFC 9449 Section 4.3, Check 5:
       * "the JWS signature can be verified with the public key in the jwk header"
       */
      describe("4.3.8 Signature verification - 'the JWS signature can be verified with the public key in the jwk header'", () => {

        it("MUST reject DPoP proof signed with different key than jwk header", async () => {
          // Generate a different key pair for signing
          const otherKeyPair = await generateDPoPKeyPair();

          // Create proof with otherKeyPair's private key but dpopKeyPair's public jwk
          const dpopProof = await createDPoPProof({
            privateKey: otherKeyPair.privateKey,  // Sign with different key
            publicJwk: dpopKeyPair.publicJwk,      // But include original public key
            htm: "POST",
            htu: serverConfig.tokenEndpoint,
          });

          const tokenResponse = await requestToken({
            endpoint: serverConfig.tokenEndpoint,
            grantType: "client_credentials",
            scope: clientSecretPostClient.scope,
            clientId: clientSecretPostClient.clientId,
            clientSecret: clientSecretPostClient.clientSecret,
            additionalHeaders: { DPoP: dpopProof },
          });

          console.log(tokenResponse.data);
          expect(tokenResponse.status).toBe(400);
          expect(tokenResponse.data.error).toBe("invalid_dpop_proof");
        });
      });
    });

    /**
     * RFC 9449 Section 5: DPoP-bound token response
     *
     * "If the authorization server decides to issue a DPoP-bound access token,
     *  the value of the token_type parameter MUST be DPoP."
     */
    describe("5.1 Token Response - 'the value of the token_type parameter MUST be DPoP'", () => {

      it("MUST return token_type 'DPoP' for DPoP-bound access tokens", async () => {
        const dpopProof = await createDPoPProof({
          privateKey: dpopKeyPair.privateKey,
          publicJwk: dpopKeyPair.publicJwk,
          htm: "POST",
          htu: serverConfig.tokenEndpoint,
        });

        const tokenResponse = await requestToken({
          endpoint: serverConfig.tokenEndpoint,
          grantType: "client_credentials",
          scope: clientSecretPostClient.scope,
          clientId: clientSecretPostClient.clientId,
          clientSecret: clientSecretPostClient.clientSecret,
          additionalHeaders: { DPoP: dpopProof },
        });

        console.log(tokenResponse.data);
        expect(tokenResponse.status).toBe(200);
        expect(tokenResponse.data.token_type).toBe("DPoP");
        expect(tokenResponse.data).toHaveProperty("access_token");
        expect(tokenResponse.data).toHaveProperty("expires_in");
      });

      it("MUST return token_type 'Bearer' when no DPoP proof is provided", async () => {
        const tokenResponse = await requestToken({
          endpoint: serverConfig.tokenEndpoint,
          grantType: "client_credentials",
          scope: clientSecretPostClient.scope,
          clientId: clientSecretPostClient.clientId,
          clientSecret: clientSecretPostClient.clientSecret,
        });

        console.log(tokenResponse.data);
        expect(tokenResponse.status).toBe(200);
        expect(tokenResponse.data.token_type).toBe("Bearer");
      });
    });

    /**
     * Malformed DPoP proof edge cases
     */
    describe("4.3.9 Malformed DPoP proofs", () => {

      it("MUST reject completely invalid DPoP proof (not a JWT)", async () => {
        const tokenResponse = await requestToken({
          endpoint: serverConfig.tokenEndpoint,
          grantType: "client_credentials",
          scope: clientSecretPostClient.scope,
          clientId: clientSecretPostClient.clientId,
          clientSecret: clientSecretPostClient.clientSecret,
          additionalHeaders: { DPoP: "not-a-valid-jwt" },
        });

        console.log(tokenResponse.data);
        expect(tokenResponse.status).toBe(400);
        expect(tokenResponse.data.error).toBe("invalid_dpop_proof");
      });

      it("MUST reject empty DPoP header value", async () => {
        const tokenResponse = await requestToken({
          endpoint: serverConfig.tokenEndpoint,
          grantType: "client_credentials",
          scope: clientSecretPostClient.scope,
          clientId: clientSecretPostClient.clientId,
          clientSecret: clientSecretPostClient.clientSecret,
          additionalHeaders: { DPoP: "" },
        });

        console.log(tokenResponse.data);
        expect(tokenResponse.status).toBe(400);
        expect(tokenResponse.data.error).toBe("invalid_dpop_proof");
      });
    });
  });

  /**
   * RFC 9449 Section 7: Resource Server DPoP Verification (UserInfo Endpoint)
   *
   * "If a resource server requires DPoP for access, it MUST check that
   *  the DPoP proof is valid and that the access token hash matches."
   *
   * Section 7.1: "When a DPoP-bound access token is presented to a resource server,
   * the resource server MUST check [...] the public key to which the access token
   * is bound matches the public key in the DPoP proof."
   *
   * Note: UserInfo endpoint requires a user-bound token (authorization code flow),
   * as client_credentials tokens have no subject (OIDC Core 5.3).
   */
  describe("Section 7: Resource Server DPoP Verification (UserInfo Endpoint)", () => {

    /**
     * RFC 9449 Section 7.1:
     * "the resource server MUST calculate the JWK Thumbprint of the public key
     *  in the DPoP proof header and check that it matches the value in the
     *  token binding."
     *
     * RFC 9449 Section 4.2:
     * "ath: hash of the access token [...] REQUIRED when the DPoP proof is
     *  used in conjunction with the presentation of an access token"
     */
    it("MUST return UserInfo claims when valid DPoP proof with ath is provided", async () => {
      const accessToken = await obtainDPoPBoundTokenViaAuthCodeFlow(dpopKeyPair);
      const ath = computeAth(accessToken);

      const userinfoProof = await createDPoPProof({
        privateKey: dpopKeyPair.privateKey,
        publicJwk: dpopKeyPair.publicJwk,
        htm: "GET",
        htu: serverConfig.userinfoEndpoint,
        overrides: { ath },
      });

      const userinfoResponse = await getUserinfo({
        endpoint: serverConfig.userinfoEndpoint,
        authorizationHeader: {
          Authorization: `DPoP ${accessToken}`,
          DPoP: userinfoProof,
        },
      });

      console.log(userinfoResponse.status, userinfoResponse.data);
      expect(userinfoResponse.status).toBe(200);
      expect(userinfoResponse.data).toHaveProperty("sub");
    });

    /**
     * RFC 9449 Section 7.1:
     * "If the DPoP proof is not present and the access token is DPoP-bound,
     *  the resource server MUST reject the request."
     */
    it("MUST reject DPoP-bound access token without DPoP proof at UserInfo", async () => {
      const accessToken = await obtainDPoPBoundTokenViaAuthCodeFlow(dpopKeyPair);

      const userinfoResponse = await getUserinfo({
        endpoint: serverConfig.userinfoEndpoint,
        authorizationHeader: {
          Authorization: `DPoP ${accessToken}`,
        },
      });

      console.log(userinfoResponse.status, userinfoResponse.data);
      expect(userinfoResponse.status).toBe(401);
    });

    /**
     * RFC 9449 Section 7.1:
     * "the resource server MUST calculate the JWK Thumbprint of the public key
     *  in the DPoP proof header and check that it matches the value in the
     *  token binding."
     */
    it("MUST reject DPoP proof signed with different key than token binding", async () => {
      const accessToken = await obtainDPoPBoundTokenViaAuthCodeFlow(dpopKeyPair);
      const ath = computeAth(accessToken);

      const otherKeyPair = await generateDPoPKeyPair();

      const userinfoProof = await createDPoPProof({
        privateKey: otherKeyPair.privateKey,
        publicJwk: otherKeyPair.publicJwk,
        htm: "GET",
        htu: serverConfig.userinfoEndpoint,
        overrides: { ath },
      });

      const userinfoResponse = await getUserinfo({
        endpoint: serverConfig.userinfoEndpoint,
        authorizationHeader: {
          Authorization: `DPoP ${accessToken}`,
          DPoP: userinfoProof,
        },
      });

      console.log(userinfoResponse.status, userinfoResponse.data);
      expect(userinfoResponse.status).toBe(401);
    });

    /**
     * RFC 9449 Section 4.2:
     * "ath: hash of the access token. The value MUST be the result of a
     *  base64url encoding of the SHA-256 hash of the ASCII encoding of
     *  the associated access token's value."
     */
    it("MUST reject DPoP proof with incorrect ath claim", async () => {
      const accessToken = await obtainDPoPBoundTokenViaAuthCodeFlow(dpopKeyPair);

      const userinfoProof = await createDPoPProof({
        privateKey: dpopKeyPair.privateKey,
        publicJwk: dpopKeyPair.publicJwk,
        htm: "GET",
        htu: serverConfig.userinfoEndpoint,
        overrides: { ath: "invalid-ath-value" },
      });

      const userinfoResponse = await getUserinfo({
        endpoint: serverConfig.userinfoEndpoint,
        authorizationHeader: {
          Authorization: `DPoP ${accessToken}`,
          DPoP: userinfoProof,
        },
      });

      console.log(userinfoResponse.status, userinfoResponse.data);
      expect(userinfoResponse.status).toBe(401);
    });

    /**
     * RFC 9449 Section 4.2:
     * "ath [...] REQUIRED when the DPoP proof is used in conjunction
     *  with the presentation of an access token"
     */
    it("MUST reject DPoP proof without ath claim at resource endpoint", async () => {
      const accessToken = await obtainDPoPBoundTokenViaAuthCodeFlow(dpopKeyPair);

      const userinfoProof = await createDPoPProof({
        privateKey: dpopKeyPair.privateKey,
        publicJwk: dpopKeyPair.publicJwk,
        htm: "GET",
        htu: serverConfig.userinfoEndpoint,
      });

      const userinfoResponse = await getUserinfo({
        endpoint: serverConfig.userinfoEndpoint,
        authorizationHeader: {
          Authorization: `DPoP ${accessToken}`,
          DPoP: userinfoProof,
        },
      });

      console.log(userinfoResponse.status, userinfoResponse.data);
      expect(userinfoResponse.status).toBe(401);
    });

    /**
     * Verify that Bearer tokens (non DPoP-bound) still work at UserInfo
     * without requiring DPoP proof.
     */
    it("SHOULD accept Bearer token without DPoP proof at UserInfo", async () => {
      // Get a Bearer token via authorization code flow (no DPoP)
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "bearer-test",
        scope: "openid profile email " + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data.token_type).toBe("Bearer");

      const userinfoResponse = await getUserinfo({
        endpoint: serverConfig.userinfoEndpoint,
        authorizationHeader: {
          Authorization: `Bearer ${tokenResponse.data.access_token}`,
        },
      });

      console.log(userinfoResponse.status, userinfoResponse.data);
      expect(userinfoResponse.status).toBe(200);
      expect(userinfoResponse.data).toHaveProperty("sub");
    });
  });
});
