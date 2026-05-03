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
import * as jose from "jose";

import { requestToken, getUserinfo, inspectToken, inspectTokenWithVerification } from "../../api/oauthClient";
import { get } from "../../lib/http";
import { backendUrl, clientSecretPostClient, serverConfig } from "../testConfig";
import { pushAuthorizations, requestAuthorizations } from "../../oauth/request";
import { calculateDPoPJkt, createDPoPProof, generateDPoPKeyPair } from "../../lib/dpop";

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


        expect(tokenResponse.status).toBe(200);
        expect(tokenResponse.data).toHaveProperty("access_token");
        expect(tokenResponse.data.token_type).toBe("DPoP");
        expect(tokenResponse.data).toHaveProperty("expires_in");
      });
    });

    /**
     * RFC 9449 Section 4.3: Checking DPoP Proofs
     *
     * "To validate a DPoP proof, the receiving server MUST ensure the following:" — followed by
     * 12 numbered checks. Each `describe` block below maps 1:1 to a Check using the verbatim
     * RFC text. File order follows the RFC's numbering.
     *
     * <ul>
     *   <li>Check 10 (server-provided nonce) is intentionally omitted — the server does not
     *       currently issue DPoP nonces.
     *   <li>Check 12 (RS-side ath / jwk binding) is covered in the Section 7 / 8 describe blocks
     *       (UserInfo, introspection, /me) where DPoP proofs are presented to a protected resource.
     * </ul>
     *
     * @see https://www.rfc-editor.org/rfc/rfc9449.html#section-4.3
     */
    describe("4.3 Checking DPoP Proofs - Validation Requirements", () => {

      describe("Check 1: There is not more than one DPoP HTTP request header field", () => {

        it("MUST reject token request with multiple DPoP headers", async () => {
          const proofA = await createDPoPProof({
            privateKey: dpopKeyPair.privateKey,
            publicJwk: dpopKeyPair.publicJwk,
            htm: "POST",
            htu: serverConfig.tokenEndpoint,
          });
          const proofB = await createDPoPProof({
            privateKey: dpopKeyPair.privateKey,
            publicJwk: dpopKeyPair.publicJwk,
            htm: "POST",
            htu: serverConfig.tokenEndpoint,
          });

          // axios serializes array header values as multiple distinct header lines
          const tokenResponse = await requestToken({
            endpoint: serverConfig.tokenEndpoint,
            grantType: "client_credentials",
            scope: clientSecretPostClient.scope,
            clientId: clientSecretPostClient.clientId,
            clientSecret: clientSecretPostClient.clientSecret,
            additionalHeaders: { DPoP: [proofA, proofB] },
          });

          expect(tokenResponse.status).toBe(400);
          expect(tokenResponse.data.error).toBe("invalid_dpop_proof");
        });
      });

      describe("Check 2: The DPoP HTTP request header field value is a single and well-formed JWT", () => {

        it("MUST reject completely invalid DPoP proof (not a JWT)", async () => {
          const tokenResponse = await requestToken({
            endpoint: serverConfig.tokenEndpoint,
            grantType: "client_credentials",
            scope: clientSecretPostClient.scope,
            clientId: clientSecretPostClient.clientId,
            clientSecret: clientSecretPostClient.clientSecret,
            additionalHeaders: { DPoP: "not-a-valid-jwt" },
          });

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

          expect(tokenResponse.status).toBe(400);
          expect(tokenResponse.data.error).toBe("invalid_dpop_proof");
        });
      });

      describe("Check 3: All required claims per Section 4.2 are contained in the JWT", () => {

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

          expect(tokenResponse.status).toBe(400);
          expect(tokenResponse.data.error).toBe("invalid_dpop_proof");
        });
      });

      describe("Check 4: The typ JOSE Header Parameter has the value dpop+jwt", () => {

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

          expect(tokenResponse.status).toBe(400);
          expect(tokenResponse.data.error).toBe("invalid_dpop_proof");
        });
      });

      describe("Check 5: The alg JOSE Header Parameter indicates a registered asymmetric digital signature algorithm, is not none, is supported by the application, and is acceptable per local policy", () => {

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

          expect(tokenResponse.status).toBe(400);
          expect(tokenResponse.data.error).toBe("invalid_dpop_proof");
        });

        it("MUST reject DPoP proof with symmetric alg (HS256)", async () => {
          // alg must be a registered ASYMMETRIC digital signature algorithm.
          const secret = crypto.randomBytes(32);
          const hsProof = await new jose.SignJWT({
            jti: uuidv4(),
            htm: "POST",
            htu: serverConfig.tokenEndpoint,
            iat: Math.floor(Date.now() / 1000),
          })
            .setProtectedHeader({ typ: "dpop+jwt", alg: "HS256", jwk: dpopKeyPair.publicJwk })
            .sign(secret);

          const tokenResponse = await requestToken({
            endpoint: serverConfig.tokenEndpoint,
            grantType: "client_credentials",
            scope: clientSecretPostClient.scope,
            clientId: clientSecretPostClient.clientId,
            clientSecret: clientSecretPostClient.clientSecret,
            additionalHeaders: { DPoP: hsProof },
          });

          expect(tokenResponse.status).toBe(400);
          expect(tokenResponse.data.error).toBe("invalid_dpop_proof");
        });

        it("MUST reject DPoP proof with alg outside dpop_signing_alg_values_supported (EdDSA)", async () => {
          // test-tenant の dpop_signing_alg_values_supported は RS*/ES*/PS* のみで EdDSA は含まれない。
          // EdDSA 自体は registered asymmetric alg だが server policy 違反として拒否されるべき。
          const { publicKey, privateKey } = await jose.generateKeyPair("EdDSA", {
            extractable: true,
          });
          const edPublicJwk = await jose.exportJWK(publicKey);
          const edProof = await new jose.SignJWT({
            jti: uuidv4(),
            htm: "POST",
            htu: serverConfig.tokenEndpoint,
            iat: Math.floor(Date.now() / 1000),
          })
            .setProtectedHeader({ typ: "dpop+jwt", alg: "EdDSA", jwk: edPublicJwk })
            .sign(privateKey);

          const tokenResponse = await requestToken({
            endpoint: serverConfig.tokenEndpoint,
            grantType: "client_credentials",
            scope: clientSecretPostClient.scope,
            clientId: clientSecretPostClient.clientId,
            clientSecret: clientSecretPostClient.clientSecret,
            additionalHeaders: { DPoP: edProof },
          });

          expect(tokenResponse.status).toBe(400);
          expect(tokenResponse.data.error).toBe("invalid_dpop_proof");
        });
      });

      describe("Check 6: The JWT signature verifies with the public key contained in the jwk JOSE Header Parameter", () => {

        it("MUST reject DPoP proof without jwk in header", async () => {
          // jwk が無いと公開鍵を取得できず、署名検証 (Check 6) の前提が成立しない。
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

          expect(tokenResponse.status).toBe(400);
          expect(tokenResponse.data.error).toBe("invalid_dpop_proof");
        });

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

          expect(tokenResponse.status).toBe(400);
          expect(tokenResponse.data.error).toBe("invalid_dpop_proof");
        });
      });

      describe("Check 7: The jwk JOSE Header Parameter does not contain a private key", () => {

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

          expect(tokenResponse.status).toBe(400);
          expect(tokenResponse.data.error).toBe("invalid_dpop_proof");
        });
      });

      describe("Check 8: The htm claim matches the HTTP method of the current request", () => {

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

          expect(tokenResponse.status).toBe(400);
          expect(tokenResponse.data.error).toBe("invalid_dpop_proof");
        });
      });

      describe("Check 9: The htu claim matches the HTTP URI value for the HTTP request in which the JWT was received, ignoring any query and fragment parts", () => {

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

          expect(tokenResponse.status).toBe(400);
          expect(tokenResponse.data.error).toBe("invalid_dpop_proof");
        });

        it("MUST accept DPoP proof when htu contains query/fragment (ignored per RFC)", async () => {
          // 比較は scheme/authority/path のみで query/fragment は無視する。
          // proof 側の htu に query/fragment を含めても scheme/host/path が一致すれば受理されるべき。
          const dpopProof = await createDPoPProof({
            privateKey: dpopKeyPair.privateKey,
            publicJwk: dpopKeyPair.publicJwk,
            htm: "POST",
            htu: `${serverConfig.tokenEndpoint}?ignored=foo#frag`,
          });

          const tokenResponse = await requestToken({
            endpoint: serverConfig.tokenEndpoint,
            grantType: "client_credentials",
            scope: clientSecretPostClient.scope,
            clientId: clientSecretPostClient.clientId,
            clientSecret: clientSecretPostClient.clientSecret,
            additionalHeaders: { DPoP: dpopProof },
          });

          expect(tokenResponse.status).toBe(200);
          expect(tokenResponse.data.token_type).toBe("DPoP");
        });
      });

      // Check 10 (server-provided nonce) — intentionally omitted; nonce issuance not implemented.

      describe("Check 11: The creation time of the JWT, as determined by either the iat claim or a server managed timestamp via the nonce claim, is within an acceptable window", () => {

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


        expect(tokenResponse.status).toBe(200);
        expect(tokenResponse.data.token_type).toBe("Bearer");
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


      expect(userinfoResponse.status).toBe(200);
      expect(userinfoResponse.data).toHaveProperty("sub");
    });
  });

  /**
   * RFC 9449 Section 7: Token Introspection with DPoP-bound Tokens
   *
   * When a DPoP-bound access token is introspected, the response MUST include:
   * - "token_type": "DPoP"
   * - "cnf" claim containing "jkt" (JWK Thumbprint) of the bound public key
   *
   * @see https://www.rfc-editor.org/rfc/rfc9449.html#section-7
   */
  describe("Section 7: Token Introspection with DPoP-bound Tokens", () => {

    it("MUST return token_type DPoP and cnf.jkt for DPoP-bound token", async () => {
      // 1. Get a DPoP-bound access token via client_credentials
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


      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data.token_type).toBe("DPoP");

      // 2. Introspect the DPoP-bound token
      const introspectionResponse = await inspectToken({
        endpoint: serverConfig.tokenIntrospectionEndpoint,
        token: tokenResponse.data.access_token,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });


      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data.active).toBe(true);
      expect(introspectionResponse.data.token_type).toBe("DPoP");
      expect(introspectionResponse.data.cnf).toBeDefined();
      expect(introspectionResponse.data.cnf.jkt).toBeDefined();
      expect(typeof introspectionResponse.data.cnf.jkt).toBe("string");
      expect(introspectionResponse.data.cnf.jkt.length).toBeGreaterThan(0);
    });

    it("MUST NOT return cnf.jkt for Bearer token (non DPoP-bound)", async () => {
      // 1. Get a Bearer token (no DPoP)
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "client_credentials",
        scope: clientSecretPostClient.scope,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data.token_type).toBe("Bearer");

      // 2. Introspect the Bearer token
      const introspectionResponse = await inspectToken({
        endpoint: serverConfig.tokenIntrospectionEndpoint,
        token: tokenResponse.data.access_token,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });


      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data.active).toBe(true);
      expect(introspectionResponse.data.token_type).not.toBe("DPoP");
      // cnf should not exist or should not contain jkt
      if (introspectionResponse.data.cnf) {
        expect(introspectionResponse.data.cnf.jkt).toBeUndefined();
      }
    });
  });

  /**
   * RFC 9449 Section 7: Token Introspection Extensions with DPoP Binding Verification
   *
   * The introspection-extensions endpoint verifies sender-constrained tokens.
   * For DPoP-bound tokens, the resource server sends a DPoP proof and the
   * authorization server verifies the JWK Thumbprint matches the token binding.
   *
   * @see https://www.rfc-editor.org/rfc/rfc9449.html#section-7
   */
  describe("Section 7: Token Introspection Extensions - DPoP Binding Verification", () => {

    it("MUST return active when valid DPoP proof matches token binding", async () => {
      // 1. Get a DPoP-bound access token
      const tokenDpopProof = await createDPoPProof({
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
        additionalHeaders: { DPoP: tokenDpopProof },
      });

      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data.token_type).toBe("DPoP");

      // 2. Introspect with DPoP proof (ath required for resource endpoint).
      // RS forwarding pattern: dpop_proof / dpop_htm / dpop_htu はすべて body parameter で渡す。
      const ath = computeAth(tokenResponse.data.access_token);
      const introspectionDpopProof = await createDPoPProof({
        privateKey: dpopKeyPair.privateKey,
        publicJwk: dpopKeyPair.publicJwk,
        htm: "POST",
        htu: serverConfig.tokenIntrospectionExtensionsEndpoint,
        overrides: { ath },
      });

      const introspectionResponse = await inspectTokenWithVerification({
        endpoint: serverConfig.tokenIntrospectionExtensionsEndpoint,
        token: tokenResponse.data.access_token,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
        dpopProof: introspectionDpopProof,
        dpopHtm: "POST",
        dpopHtu: serverConfig.tokenIntrospectionExtensionsEndpoint,
      });


      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data.active).toBe(true);
      expect(introspectionResponse.data.token_type).toBe("DPoP");
      expect(introspectionResponse.data.cnf).toBeDefined();
      expect(introspectionResponse.data.cnf.jkt).toBeDefined();
    });

    it("MUST reject when DPoP proof is signed with different key than token binding", async () => {
      // 1. Get a DPoP-bound access token with original key
      const tokenDpopProof = await createDPoPProof({
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
        additionalHeaders: { DPoP: tokenDpopProof },
      });

      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data.token_type).toBe("DPoP");

      // 2. Introspect with DPoP proof signed by DIFFERENT key (RS forwarding pattern: body 渡し)
      const otherKeyPair = await generateDPoPKeyPair();
      const ath = computeAth(tokenResponse.data.access_token);
      const introspectionDpopProof = await createDPoPProof({
        privateKey: otherKeyPair.privateKey,
        publicJwk: otherKeyPair.publicJwk,
        htm: "POST",
        htu: serverConfig.tokenIntrospectionExtensionsEndpoint,
        overrides: { ath },
      });

      const introspectionResponse = await inspectTokenWithVerification({
        endpoint: serverConfig.tokenIntrospectionExtensionsEndpoint,
        token: tokenResponse.data.access_token,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
        dpopProof: introspectionDpopProof,
        dpopHtm: "POST",
        dpopHtu: serverConfig.tokenIntrospectionExtensionsEndpoint,
      });


      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data.active).toBe(false);
      expect(introspectionResponse.data.status_code).toBe(401);
      expect(introspectionResponse.data.error).toBe("invalid_token");
    });

    it("MUST reject DPoP-bound token without DPoP proof at extensions endpoint", async () => {
      // 1. Get a DPoP-bound access token
      const tokenDpopProof = await createDPoPProof({
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
        additionalHeaders: { DPoP: tokenDpopProof },
      });

      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data.token_type).toBe("DPoP");

      // 2. Introspect WITHOUT DPoP proof
      const introspectionResponse = await inspectTokenWithVerification({
        endpoint: serverConfig.tokenIntrospectionExtensionsEndpoint,
        token: tokenResponse.data.access_token,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });


      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data.active).toBe(false);
      expect(introspectionResponse.data.status_code).toBe(401);
      expect(introspectionResponse.data.error).toBe("invalid_token");
    });

    it("MUST accept Bearer token without DPoP proof at extensions endpoint", async () => {
      // 1. Get a Bearer token (no DPoP)
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "client_credentials",
        scope: clientSecretPostClient.scope,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data.token_type).toBe("Bearer");

      // 2. Introspect without DPoP proof (should succeed for Bearer token)
      const introspectionResponse = await inspectTokenWithVerification({
        endpoint: serverConfig.tokenIntrospectionExtensionsEndpoint,
        token: tokenResponse.data.access_token,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });


      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data.active).toBe(true);
    });
  });

  /**
   * Section 8: Protected Resource Access with DPoP-bound Tokens
   *
   * RFC 9449 Section 7.1: Resource servers MUST verify that the DPoP proof
   * is valid and that the public key matches the token's cnf.jkt binding.
   *
   * This tests the ProtectedResourceApiFilter which guards /me APIs.
   */
  describe("Section 8: Protected Resource API (/me) - DPoP Binding Verification", () => {

    const meEndpoint = `${backendUrl}/${serverConfig.tenantId}/v1/me`;

    const getResourceOwnerDPoPToken = async (keyPair) => {
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
        scope: "openid profile email",
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
        additionalHeaders: { DPoP: dpopProof },
      });

      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data.token_type).toBe("DPoP");
      return tokenResponse.data.access_token;
    };

    it("MUST accept /me request with valid DPoP proof for DPoP-bound token", async () => {
      const accessToken = await getResourceOwnerDPoPToken(dpopKeyPair);

      const ath = computeAth(accessToken);
      const dpopProof = await createDPoPProof({
        privateKey: dpopKeyPair.privateKey,
        publicJwk: dpopKeyPair.publicJwk,
        htm: "GET",
        htu: meEndpoint,
        overrides: { ath },
      });

      const response = await get({
        url: meEndpoint,
        headers: {
          Authorization: `DPoP ${accessToken}`,
          DPoP: dpopProof,
        },
      });

      // Filter passes DPoP verification; actual /me GET may return 404/405 etc.
      // but NOT 401 with "DPoP" or "invalid_token" error
      expect(response.status).not.toBe(401);
    });

    it("MUST reject /me request when DPoP-bound token has no DPoP proof", async () => {
      const accessToken = await getResourceOwnerDPoPToken(dpopKeyPair);

      const response = await get({
        url: meEndpoint,
        headers: {
          Authorization: `DPoP ${accessToken}`,
        },
      });

      expect(response.status).toBe(401);
    });

    it("MUST reject /me request when DPoP proof is signed with different key", async () => {
      const accessToken = await getResourceOwnerDPoPToken(dpopKeyPair);

      const otherKeyPair = await generateDPoPKeyPair();
      const ath = computeAth(accessToken);
      const dpopProof = await createDPoPProof({
        privateKey: otherKeyPair.privateKey,
        publicJwk: otherKeyPair.publicJwk,
        htm: "GET",
        htu: meEndpoint,
        overrides: { ath },
      });

      const response = await get({
        url: meEndpoint,
        headers: {
          Authorization: `DPoP ${accessToken}`,
          DPoP: dpopProof,
        },
      });

      expect(response.status).toBe(401);
    });

    it("MUST accept /me request with Bearer token without DPoP proof", async () => {
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "password",
        username: serverConfig.oauth.username,
        password: serverConfig.oauth.password,
        scope: "openid profile email",
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data.token_type).toBe("Bearer");

      const response = await get({
        url: meEndpoint,
        headers: {
          Authorization: `Bearer ${tokenResponse.data.access_token}`,
        },
      });

      // Bearer token without DPoP should pass the filter (not 401 unauthorized)
      expect(response.status).toBeLessThan(500);
      expect(response.status).not.toBe(401);
    });
  });

  describe("Section 5: Refresh Token DPoP Behavior", () => {

    it("MUST accept refresh with same DPoP key that was used for original token", async () => {
      // 1. Get authorization code
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "dpop-refresh-test",
        scope: "openid profile email " + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });
      expect(authorizationResponse.code).toBeDefined();

      // 2. Exchange code with DPoP proof to get DPoP-bound token + refresh_token
      const tokenDpopProof = await createDPoPProof({
        privateKey: dpopKeyPair.privateKey,
        publicJwk: dpopKeyPair.publicJwk,
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
        additionalHeaders: { DPoP: tokenDpopProof },
      });

      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data.token_type).toBe("DPoP");
      expect(tokenResponse.data.refresh_token).toBeDefined();

      // 3. Refresh with same DPoP key
      const refreshDpopProof = await createDPoPProof({
        privateKey: dpopKeyPair.privateKey,
        publicJwk: dpopKeyPair.publicJwk,
        htm: "POST",
        htu: serverConfig.tokenEndpoint,
      });

      const refreshResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        refreshToken: tokenResponse.data.refresh_token,
        grantType: "refresh_token",
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
        additionalHeaders: { DPoP: refreshDpopProof },
      });

      expect(refreshResponse.status).toBe(200);
      expect(refreshResponse.data.token_type).toBe("DPoP");
      expect(refreshResponse.data.access_token).toBeDefined();
    });

    it("MUST allow confidential client to refresh with rotated DPoP key (RFC 9449 §5)", async () => {
      // RFC 9449 §5: Refresh tokens issued to confidential clients are NOT bound to the DPoP
      // public key. They are sender-constrained by client authentication, so the DPoP key MAY
      // rotate between issuance and refresh without invalidating the refresh token.
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "dpop-refresh-rotated-key",
        scope: "openid profile email " + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });
      expect(authorizationResponse.code).toBeDefined();

      const tokenDpopProof = await createDPoPProof({
        privateKey: dpopKeyPair.privateKey,
        publicJwk: dpopKeyPair.publicJwk,
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
        additionalHeaders: { DPoP: tokenDpopProof },
      });

      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data.token_type).toBe("DPoP");
      expect(tokenResponse.data.refresh_token).toBeDefined();

      // Refresh with a freshly generated (rotated) DPoP key — confidential clients MUST be allowed.
      const rotatedKeyPair = await generateDPoPKeyPair();
      const rotatedDpopProof = await createDPoPProof({
        privateKey: rotatedKeyPair.privateKey,
        publicJwk: rotatedKeyPair.publicJwk,
        htm: "POST",
        htu: serverConfig.tokenEndpoint,
      });

      const refreshResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        refreshToken: tokenResponse.data.refresh_token,
        grantType: "refresh_token",
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
        additionalHeaders: { DPoP: rotatedDpopProof },
      });

      expect(refreshResponse.status).toBe(200);
      expect(refreshResponse.data.token_type).toBe("DPoP");
      expect(refreshResponse.data.access_token).toBeDefined();
    });

    it("MUST reject refresh without DPoP proof when original token was DPoP-bound (RFC 6749 §5.2 invalid_request)", async () => {
      // 1. Get authorization code
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "dpop-refresh-no-proof",
        scope: "openid profile email " + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
      });
      expect(authorizationResponse.code).toBeDefined();

      // 2. Exchange code with DPoP proof
      const tokenDpopProof = await createDPoPProof({
        privateKey: dpopKeyPair.privateKey,
        publicJwk: dpopKeyPair.publicJwk,
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
        additionalHeaders: { DPoP: tokenDpopProof },
      });

      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data.token_type).toBe("DPoP");
      expect(tokenResponse.data.refresh_token).toBeDefined();

      // 3. Refresh WITHOUT DPoP proof - must be rejected (downgrade to Bearer)
      const refreshResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        refreshToken: tokenResponse.data.refresh_token,
        grantType: "refresh_token",
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      expect(refreshResponse.status).toBe(400);
      // Missing DPoP header = "missing required parameter" per RFC 6749 §5.2, not invalid_dpop_proof.
      expect(refreshResponse.data.error).toBe("invalid_request");
    });
  });

  /**
   * RFC 9449 Section 10.1: PAR + DPoP integration
   *
   * "If the dpop_jkt parameter is included in a PAR request, it MUST match the JWK Thumbprint of
   *  the public key from the DPoP HTTP request header field if a DPoP proof is also included."
   *
   * "If the dpop_jkt parameter is omitted from a PAR request that includes a DPoP HTTP request
   *  header field, the authorization server MUST set dpop_jkt to the JWK Thumbprint of the public
   *  key from that DPoP proof."
   */
  describe("Section 10.1: PAR + DPoP integration", () => {

    const computeJwkThumbprint = calculateDPoPJkt;

    it("MUST bind dpop_jkt to PAR-issued request_uri when only DPoP proof is presented (no dpop_jkt parameter)", async () => {
      const parEndpoint = serverConfig.authorizationEndpoint + "/push";
      const parDpopProof = await createDPoPProof({
        privateKey: dpopKeyPair.privateKey,
        publicJwk: dpopKeyPair.publicJwk,
        htm: "POST",
        htu: parEndpoint,
      });

      const parResponse = await pushAuthorizations({
        endpoint: parEndpoint,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
        responseType: "code",
        state: "par-dpop-bind",
        scope: "openid " + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
        additionalHeaders: { DPoP: parDpopProof },
      });
      expect(parResponse.status).toBe(201);
      const requestUri = parResponse.data.request_uri;
      expect(requestUri).toBeDefined();

      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        requestUri,
      });
      expect(authorizationResponse.code).toBeDefined();

      const tokenDpopProof = await createDPoPProof({
        privateKey: dpopKeyPair.privateKey,
        publicJwk: dpopKeyPair.publicJwk,
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
        additionalHeaders: { DPoP: tokenDpopProof },
      });

      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data.token_type).toBe("DPoP");
    });

    it("MUST reject token request when token DPoP key differs from PAR DPoP key", async () => {
      const parEndpoint = serverConfig.authorizationEndpoint + "/push";
      const parDpopProof = await createDPoPProof({
        privateKey: dpopKeyPair.privateKey,
        publicJwk: dpopKeyPair.publicJwk,
        htm: "POST",
        htu: parEndpoint,
      });

      const parResponse = await pushAuthorizations({
        endpoint: parEndpoint,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
        responseType: "code",
        state: "par-dpop-mismatch",
        scope: "openid " + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
        additionalHeaders: { DPoP: parDpopProof },
      });
      expect(parResponse.status).toBe(201);

      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        requestUri: parResponse.data.request_uri,
      });
      expect(authorizationResponse.code).toBeDefined();

      const otherKeyPair = await generateDPoPKeyPair();
      const wrongDpopProof = await createDPoPProof({
        privateKey: otherKeyPair.privateKey,
        publicJwk: otherKeyPair.publicJwk,
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
        additionalHeaders: { DPoP: wrongDpopProof },
      });

      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.data.error).toBe("invalid_grant");
    });

    it("MUST accept PAR with both dpop_jkt parameter and DPoP proof when they match", async () => {
      const jkt = await computeJwkThumbprint(dpopKeyPair.publicJwk);
      const parEndpoint = serverConfig.authorizationEndpoint + "/push";
      const parDpopProof = await createDPoPProof({
        privateKey: dpopKeyPair.privateKey,
        publicJwk: dpopKeyPair.publicJwk,
        htm: "POST",
        htu: parEndpoint,
      });

      const parResponse = await pushAuthorizations({
        endpoint: parEndpoint,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
        responseType: "code",
        state: "par-dpop-both-match",
        scope: "openid " + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
        dpopJkt: jkt,
        additionalHeaders: { DPoP: parDpopProof },
      });
      expect(parResponse.status).toBe(201);
      expect(parResponse.data.request_uri).toBeDefined();
    });

    it("MUST reject PAR when dpop_jkt parameter mismatches DPoP proof JWK thumbprint", async () => {
      const wrongJkt = await computeJwkThumbprint((await generateDPoPKeyPair()).publicJwk);
      const parEndpoint = serverConfig.authorizationEndpoint + "/push";
      const parDpopProof = await createDPoPProof({
        privateKey: dpopKeyPair.privateKey,
        publicJwk: dpopKeyPair.publicJwk,
        htm: "POST",
        htu: parEndpoint,
      });

      const parResponse = await pushAuthorizations({
        endpoint: parEndpoint,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
        responseType: "code",
        state: "par-dpop-both-mismatch",
        scope: "openid " + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
        dpopJkt: wrongJkt,
        additionalHeaders: { DPoP: parDpopProof },
      });

      expect(parResponse.status).toBeGreaterThanOrEqual(400);
      expect(parResponse.status).toBeLessThan(500);
    });
  });

  /**
   * RFC 9449 Section 10: Authorization Code Binding to a DPoP Key
   *
   * "The dpop_jkt authorization request parameter, if present, MUST be a JSON Web Key (JWK)
   *  Thumbprint [RFC7638] of the public key the client wishes to use to bind the access token to."
   *
   * "If the value of the dpop_jkt authorization request parameter does not match the JWK Thumbprint
   *  of the DPoP proof JWT presented at the token endpoint, the authorization server MUST refuse
   *  the request and respond with an invalid_grant error."
   */
  describe("Section 10: Authorization Code Binding to a DPoP Key (dpop_jkt)", () => {

    const computeJwkThumbprint = calculateDPoPJkt;

    it("MUST issue an authorization code when dpop_jkt is provided", async () => {
      const jkt = await computeJwkThumbprint(dpopKeyPair.publicJwk);

      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "dpop-jkt-test",
        scope: "openid " + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
        dpopJkt: jkt,
      });

      expect(authorizationResponse.code).toBeDefined();
    });

    it("MUST exchange code for token when DPoP proof matches dpop_jkt", async () => {
      const jkt = await computeJwkThumbprint(dpopKeyPair.publicJwk);

      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "dpop-jkt-match",
        scope: "openid " + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
        dpopJkt: jkt,
      });
      expect(authorizationResponse.code).toBeDefined();

      const dpopProof = await createDPoPProof({
        privateKey: dpopKeyPair.privateKey,
        publicJwk: dpopKeyPair.publicJwk,
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
    });

    it("MUST reject token request when DPoP proof key does not match dpop_jkt (invalid_grant)", async () => {
      const jkt = await computeJwkThumbprint(dpopKeyPair.publicJwk);

      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "dpop-jkt-mismatch",
        scope: "openid " + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
        dpopJkt: jkt,
      });
      expect(authorizationResponse.code).toBeDefined();

      const otherKeyPair = await generateDPoPKeyPair();
      const wrongDpopProof = await createDPoPProof({
        privateKey: otherKeyPair.privateKey,
        publicJwk: otherKeyPair.publicJwk,
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
        additionalHeaders: { DPoP: wrongDpopProof },
      });

      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.data.error).toBe("invalid_grant");
    });

    it("MUST reject token request when dpop_jkt is bound but no DPoP proof is presented", async () => {
      const jkt = await computeJwkThumbprint(dpopKeyPair.publicJwk);

      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "dpop-jkt-no-proof",
        scope: "openid " + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
        dpopJkt: jkt,
      });
      expect(authorizationResponse.code).toBeDefined();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.data.error).toBe("invalid_grant");
    });
  });
});
