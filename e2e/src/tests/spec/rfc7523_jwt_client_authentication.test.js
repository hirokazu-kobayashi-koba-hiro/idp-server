/**
 * RFC 7523: JSON Web Token (JWT) Profile for OAuth 2.0 Client Authentication
 * and Authorization Grants
 *
 * This test suite verifies compliance with RFC 7523 Section 3
 * "JWT Format and Processing Requirements" for JWT-based client authentication.
 *
 * @see https://www.rfc-editor.org/rfc/rfc7523
 */
import { describe, expect, it } from "@jest/globals";

import { requestToken } from "../../api/oauthClient";
import {
  clientSecretJwtClient,
  privateKeyJwtClient,
  serverConfig,
} from "../testConfig";
import { requestAuthorizations } from "../../oauth/request";
import {
  createClientAssertion,
  createUnsignedClientAssertion,
  createCustomClientAssertion,
} from "../../lib/oauth";
import { toEpocTime } from "../../lib/util";

/**
 * Helper function to get authorization code for testing
 */
const getAuthorizationCode = async (client) => {
  const { authorizationResponse } = await requestAuthorizations({
    endpoint: serverConfig.authorizationEndpoint,
    responseType: "code",
    state: "rfc7523-test-" + Date.now(),
    scope: "openid profile " + client.scope,
    redirectUri: client.redirectUri,
    clientId: client.clientId,
  });
  return authorizationResponse.code;
};

/**
 * Helper function to make token request with client assertion
 */
const makeTokenRequest = async (client, code, clientAssertion) => {
  return await requestToken({
    endpoint: serverConfig.tokenEndpoint,
    code,
    grantType: "authorization_code",
    redirectUri: client.redirectUri,
    clientId: client.clientId,
    clientAssertion,
    clientAssertionType: "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
  });
};

/**
 * RFC 7523: JSON Web Token (JWT) Profile for OAuth 2.0 Client Authentication
 *
 * Section 3 defines the JWT format and processing requirements that MUST be
 * followed when using JWTs for client authentication.
 */
describe("RFC 7523: JSON Web Token (JWT) Profile for OAuth 2.0 Client Authentication", () => {

  /**
   * RFC 7523 Section 3 - JWT Format and Processing Requirements
   *
   * This section specifies the required claims and validation rules
   * for JWTs used in client authentication.
   */
  describe("Section 3: JWT Format and Processing Requirements", () => {

    /**
     * RFC 7523 Section 3, Paragraph 1:
     *
     * "The JWT MUST be digitally signed or have a Message Authentication Code (MAC)
     *  applied by the issuer."
     *
     * RFC 7523 Section 3, Paragraph 10:
     *
     * "The authorization server MUST reject JWTs with an invalid signature or MAC value."
     *
     * These requirements ensure that JWTs cannot be forged or tampered with.
     * The "alg: none" attack vector MUST be prevented.
     */
    describe("3.1 Signature/MAC Requirements - 'The JWT MUST be digitally signed or have a Message Authentication Code (MAC) applied by the issuer.'", () => {

      /**
       * Test: Reject unsigned JWT (alg: none) for client_secret_jwt
       *
       * RFC 7523 Section 3:
       * "The JWT MUST be digitally signed or have a Message Authentication Code (MAC)
       *  applied by the issuer."
       *
       * An unsigned JWT (alg: none) violates this requirement and MUST be rejected.
       */
      it("client_secret_jwt: MUST reject JWT with 'alg: none' - unsigned JWTs violate the signature requirement", async () => {
        const code = await getAuthorizationCode(clientSecretJwtClient);
        const unsignedAssertion = createUnsignedClientAssertion({
          client: clientSecretJwtClient,
          issuer: serverConfig.issuer,
        });

        const response = await makeTokenRequest(clientSecretJwtClient, code, unsignedAssertion);

        expect(response.status).toBe(401);
        expect(response.data).toHaveProperty("error", "invalid_client");
      });

      /**
       * Test: Reject unsigned JWT (alg: none) for private_key_jwt
       *
       * RFC 7523 Section 3:
       * "The JWT MUST be digitally signed or have a Message Authentication Code (MAC)
       *  applied by the issuer."
       *
       * An unsigned JWT (alg: none) violates this requirement and MUST be rejected.
       */
      it("private_key_jwt: MUST reject JWT with 'alg: none' - unsigned JWTs violate the signature requirement", async () => {
        const code = await getAuthorizationCode(privateKeyJwtClient);
        const unsignedAssertion = createUnsignedClientAssertion({
          client: privateKeyJwtClient,
          issuer: serverConfig.issuer,
        });

        const response = await makeTokenRequest(privateKeyJwtClient, code, unsignedAssertion);

        expect(response.status).toBe(401);
        expect(response.data).toHaveProperty("error", "invalid_client");
      });

      /**
       * Test: Accept properly signed JWT for client_secret_jwt
       *
       * RFC 7523 Section 3:
       * "The JWT MUST be digitally signed or have a Message Authentication Code (MAC)
       *  applied by the issuer."
       *
       * A properly signed JWT with a valid MAC MUST be accepted.
       */
      it("client_secret_jwt: MUST accept JWT with valid HMAC signature", async () => {
        const code = await getAuthorizationCode(clientSecretJwtClient);
        const signedAssertion = createClientAssertion({
          client: clientSecretJwtClient,
          issuer: serverConfig.issuer,
        });

        const response = await makeTokenRequest(clientSecretJwtClient, code, signedAssertion);

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("access_token");
      });

      /**
       * Test: Accept properly signed JWT for private_key_jwt
       *
       * RFC 7523 Section 3:
       * "The JWT MUST be digitally signed or have a Message Authentication Code (MAC)
       *  applied by the issuer."
       *
       * A properly signed JWT with a valid digital signature MUST be accepted.
       */
      it("private_key_jwt: MUST accept JWT with valid digital signature", async () => {
        const code = await getAuthorizationCode(privateKeyJwtClient);
        const signedAssertion = createClientAssertion({
          client: privateKeyJwtClient,
          issuer: serverConfig.issuer,
        });

        const response = await makeTokenRequest(privateKeyJwtClient, code, signedAssertion);

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("access_token");
      });
    });

    /**
     * RFC 7523 Section 3, Requirement 1:
     *
     * "1. The JWT MUST contain an 'iss' (issuer) claim that contains a
     *     unique identifier for the entity that issued the JWT. In the
     *     case of a client authenticating with its client credentials,
     *     the value of the 'iss' claim MUST be the 'client_id' of the
     *     OAuth client."
     */
    describe("3.2 'iss' (issuer) claim - 'The JWT MUST contain an iss claim that contains a unique identifier for the entity that issued the JWT.'", () => {

      /**
       * Test: Reject JWT without iss claim
       *
       * RFC 7523 Section 3, Requirement 1:
       * "The JWT MUST contain an 'iss' (issuer) claim..."
       *
       * Missing iss claim violates this MUST requirement.
       */
      it("client_secret_jwt: MUST reject JWT without 'iss' claim", async () => {
        const code = await getAuthorizationCode(clientSecretJwtClient);
        const assertion = createCustomClientAssertion({
          client: clientSecretJwtClient,
          issuer: serverConfig.issuer,
          omit: ["iss"],
        });

        const response = await makeTokenRequest(clientSecretJwtClient, code, assertion);

        expect(response.status).toBe(401);
        expect(response.data).toHaveProperty("error", "invalid_client");
      });

      /**
       * Test: Reject JWT with iss claim not matching client_id
       *
       * RFC 7523 Section 3, Requirement 1:
       * "...the value of the 'iss' claim MUST be the 'client_id' of the OAuth client."
       *
       * iss claim value not matching client_id violates this requirement.
       */
      it("client_secret_jwt: MUST reject JWT with 'iss' claim not matching 'client_id'", async () => {
        const code = await getAuthorizationCode(clientSecretJwtClient);
        const assertion = createCustomClientAssertion({
          client: clientSecretJwtClient,
          issuer: serverConfig.issuer,
          overrides: { iss: "wrong-issuer-id" },
        });

        const response = await makeTokenRequest(clientSecretJwtClient, code, assertion);

        expect(response.status).toBe(401);
        expect(response.data).toHaveProperty("error", "invalid_client");
      });

      /**
       * Test: Reject JWT without iss claim for private_key_jwt
       */
      it("private_key_jwt: MUST reject JWT without 'iss' claim", async () => {
        const code = await getAuthorizationCode(privateKeyJwtClient);
        const assertion = createCustomClientAssertion({
          client: privateKeyJwtClient,
          issuer: serverConfig.issuer,
          omit: ["iss"],
        });

        const response = await makeTokenRequest(privateKeyJwtClient, code, assertion);

        expect(response.status).toBe(401);
        expect(response.data).toHaveProperty("error", "invalid_client");
      });
    });

    /**
     * RFC 7523 Section 3, Requirement 2:
     *
     * "2. The JWT MUST contain a 'sub' (subject) claim identifying the
     *     principal that is the subject of the JWT. In the case of a
     *     client authenticating with its client credentials, the value
     *     of the 'sub' claim MUST be the 'client_id' of the OAuth client."
     */
    describe("3.3 'sub' (subject) claim - 'The JWT MUST contain a sub claim identifying the principal that is the subject of the JWT.'", () => {

      /**
       * Test: Reject JWT without sub claim
       *
       * RFC 7523 Section 3, Requirement 2:
       * "The JWT MUST contain a 'sub' (subject) claim..."
       *
       * Missing sub claim violates this MUST requirement.
       */
      it("client_secret_jwt: MUST reject JWT without 'sub' claim", async () => {
        const code = await getAuthorizationCode(clientSecretJwtClient);
        const assertion = createCustomClientAssertion({
          client: clientSecretJwtClient,
          issuer: serverConfig.issuer,
          omit: ["sub"],
        });

        const response = await makeTokenRequest(clientSecretJwtClient, code, assertion);

        expect(response.status).toBe(401);
        expect(response.data).toHaveProperty("error", "invalid_client");
      });

      /**
       * Test: Reject JWT with sub claim not identical to client_id
       *
       * RFC 7523 Section 3, Requirement 2:
       * "...the value of the 'sub' claim MUST be the 'client_id' of the OAuth client."
       *
       * sub claim value not matching client_id violates this requirement.
       */
      it("client_secret_jwt: MUST reject JWT with 'sub' claim not matching 'client_id'", async () => {
        const code = await getAuthorizationCode(clientSecretJwtClient);
        const assertion = createCustomClientAssertion({
          client: clientSecretJwtClient,
          issuer: serverConfig.issuer,
          overrides: { sub: "different-subject" },
        });

        const response = await makeTokenRequest(clientSecretJwtClient, code, assertion);

        expect(response.status).toBe(401);
        expect(response.data).toHaveProperty("error", "invalid_client");
      });

      /**
       * Test: Reject JWT without sub claim for private_key_jwt
       */
      it("private_key_jwt: MUST reject JWT without 'sub' claim", async () => {
        const code = await getAuthorizationCode(privateKeyJwtClient);
        const assertion = createCustomClientAssertion({
          client: privateKeyJwtClient,
          issuer: serverConfig.issuer,
          omit: ["sub"],
        });

        const response = await makeTokenRequest(privateKeyJwtClient, code, assertion);

        expect(response.status).toBe(401);
        expect(response.data).toHaveProperty("error", "invalid_client");
      });
    });

    /**
     * RFC 7523 Section 3, Requirement 3:
     *
     * "3. The JWT MUST contain an 'aud' (audience) claim containing a
     *     value that identifies the authorization server as an intended
     *     audience. The token endpoint URL of the authorization server
     *     MAY be used as a value for the 'aud' element to identify the
     *     authorization server as an intended audience of the JWT."
     */
    describe("3.4 'aud' (audience) claim - 'The JWT MUST contain an aud claim containing a value that identifies the authorization server as an intended audience.'", () => {

      /**
       * Test: Reject JWT without aud claim
       *
       * RFC 7523 Section 3, Requirement 3:
       * "The JWT MUST contain an 'aud' (audience) claim..."
       *
       * Missing aud claim violates this MUST requirement.
       */
      it("client_secret_jwt: MUST reject JWT without 'aud' claim", async () => {
        const code = await getAuthorizationCode(clientSecretJwtClient);
        const assertion = createCustomClientAssertion({
          client: clientSecretJwtClient,
          issuer: serverConfig.issuer,
          omit: ["aud"],
        });

        const response = await makeTokenRequest(clientSecretJwtClient, code, assertion);

        expect(response.status).toBe(401);
        expect(response.data).toHaveProperty("error", "invalid_client");
      });

      /**
       * Test: Reject JWT with aud claim not containing authorization server identifier
       *
       * RFC 7523 Section 3, Requirement 3:
       * "...containing a value that identifies the authorization server as an intended audience."
       *
       * aud claim not identifying the authorization server violates this requirement.
       */
      it("client_secret_jwt: MUST reject JWT with 'aud' claim not identifying the authorization server", async () => {
        const code = await getAuthorizationCode(clientSecretJwtClient);
        const assertion = createCustomClientAssertion({
          client: clientSecretJwtClient,
          issuer: serverConfig.issuer,
          overrides: { aud: "https://wrong-server.example.com" },
        });

        const response = await makeTokenRequest(clientSecretJwtClient, code, assertion);

        expect(response.status).toBe(401);
        expect(response.data).toHaveProperty("error", "invalid_client");
      });

      /**
       * Test: Accept JWT with aud claim containing token endpoint URL
       *
       * RFC 7523 Section 3, Requirement 3:
       * "The token endpoint URL of the authorization server MAY be used as
       *  a value for the 'aud' element to identify the authorization server
       *  as an intended audience of the JWT."
       *
       * Token endpoint URL is explicitly allowed as a valid aud value.
       */
      it("client_secret_jwt: MAY accept JWT with 'aud' claim set to token endpoint URL", async () => {
        const code = await getAuthorizationCode(clientSecretJwtClient);
        const assertion = createCustomClientAssertion({
          client: clientSecretJwtClient,
          issuer: serverConfig.issuer,
          overrides: { aud: serverConfig.tokenEndpoint },
        });

        const response = await makeTokenRequest(clientSecretJwtClient, code, assertion);

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("access_token");
      });

      /**
       * Test: Reject JWT without aud claim for private_key_jwt
       */
      it("private_key_jwt: MUST reject JWT without 'aud' claim", async () => {
        const code = await getAuthorizationCode(privateKeyJwtClient);
        const assertion = createCustomClientAssertion({
          client: privateKeyJwtClient,
          issuer: serverConfig.issuer,
          omit: ["aud"],
        });

        const response = await makeTokenRequest(privateKeyJwtClient, code, assertion);

        expect(response.status).toBe(401);
        expect(response.data).toHaveProperty("error", "invalid_client");
      });
    });

    /**
     * RFC 7523 Section 3, Requirement 4:
     *
     * "4. The JWT MUST contain an 'exp' (expiration time) claim that
     *     limits the time window during which the JWT can be used. The
     *     authorization server MUST reject any JWT with an expiration
     *     time that has passed, subject to allowable clock skew between
     *     systems."
     */
    describe("3.5 'exp' (expiration time) claim - 'The JWT MUST contain an exp claim that limits the time window during which the JWT can be used.'", () => {

      /**
       * Test: Reject JWT without exp claim
       *
       * RFC 7523 Section 3, Requirement 4:
       * "The JWT MUST contain an 'exp' (expiration time) claim..."
       *
       * Missing exp claim violates this MUST requirement.
       */
      it("client_secret_jwt: MUST reject JWT without 'exp' claim", async () => {
        const code = await getAuthorizationCode(clientSecretJwtClient);
        const assertion = createCustomClientAssertion({
          client: clientSecretJwtClient,
          issuer: serverConfig.issuer,
          omit: ["exp"],
        });

        const response = await makeTokenRequest(clientSecretJwtClient, code, assertion);

        expect(response.status).toBe(401);
        expect(response.data).toHaveProperty("error", "invalid_client");
      });

      /**
       * Test: Reject expired JWT
       *
       * RFC 7523 Section 3, Requirement 4:
       * "The authorization server MUST reject any JWT with an expiration time that has passed..."
       *
       * Expired JWT (exp in the past) MUST be rejected.
       */
      it("client_secret_jwt: MUST reject JWT with 'exp' claim in the past (expired)", async () => {
        const code = await getAuthorizationCode(clientSecretJwtClient);
        const assertion = createCustomClientAssertion({
          client: clientSecretJwtClient,
          issuer: serverConfig.issuer,
          overrides: { exp: toEpocTime({ adjusted: -3600 }) }, // 1 hour ago
        });

        const response = await makeTokenRequest(clientSecretJwtClient, code, assertion);

        expect(response.status).toBe(401);
        expect(response.data).toHaveProperty("error", "invalid_client");
      });

      /**
       * Test: Reject JWT without exp claim for private_key_jwt
       */
      it("private_key_jwt: MUST reject JWT without 'exp' claim", async () => {
        const code = await getAuthorizationCode(privateKeyJwtClient);
        const assertion = createCustomClientAssertion({
          client: privateKeyJwtClient,
          issuer: serverConfig.issuer,
          omit: ["exp"],
        });

        const response = await makeTokenRequest(privateKeyJwtClient, code, assertion);

        expect(response.status).toBe(401);
        expect(response.data).toHaveProperty("error", "invalid_client");
      });

      /**
       * Test: Reject expired JWT for private_key_jwt
       */
      it("private_key_jwt: MUST reject JWT with 'exp' claim in the past (expired)", async () => {
        const code = await getAuthorizationCode(privateKeyJwtClient);
        const assertion = createCustomClientAssertion({
          client: privateKeyJwtClient,
          issuer: serverConfig.issuer,
          overrides: { exp: toEpocTime({ adjusted: -3600 }) },
        });

        const response = await makeTokenRequest(privateKeyJwtClient, code, assertion);

        expect(response.status).toBe(401);
        expect(response.data).toHaveProperty("error", "invalid_client");
      });
    });

    /**
     * RFC 7523 Section 3, Requirement 7:
     *
     * "7. The JWT MAY contain a 'jti' (JWT ID) claim that provides a
     *     unique identifier for the token. The authorization server MAY
     *     ensure that JWTs are not replayed by maintaining the set of
     *     used 'jti' values for the length of time for which the JWT
     *     would be considered valid based on the applicable 'exp'
     *     instant."
     *
     * Note: While jti is OPTIONAL per RFC 7523, this implementation
     * REQUIRES it for replay protection as a security enhancement.
     */
    describe("3.6 'jti' (JWT ID) claim - 'The JWT MAY contain a jti claim that provides a unique identifier for the token.' (Implementation: REQUIRED for replay protection)", () => {

      /**
       * Test: Reject JWT without jti claim (implementation-specific requirement)
       *
       * RFC 7523 Section 3, Requirement 7:
       * "The JWT MAY contain a 'jti' (JWT ID) claim..."
       *
       * While RFC 7523 makes jti optional, this implementation requires it
       * for replay protection to enhance security.
       */
      it("client_secret_jwt: Implementation MUST reject JWT without 'jti' claim (replay protection)", async () => {
        const code = await getAuthorizationCode(clientSecretJwtClient);
        const assertion = createCustomClientAssertion({
          client: clientSecretJwtClient,
          issuer: serverConfig.issuer,
          omit: ["jti"],
        });

        const response = await makeTokenRequest(clientSecretJwtClient, code, assertion);

        expect(response.status).toBe(401);
        expect(response.data).toHaveProperty("error", "invalid_client");
      });
    });
  });
});
