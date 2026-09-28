/**
 * Helpers for the user bound Client Instance registration (Issue #1521).
 *
 * A registration is authenticated by an ID token whose nonce is
 *
 *   request_hash = base64url_nopad( SHA-256( challenge_bytes || canonical_jwk_utf8 ) )
 *
 * so the ID token only authenticates the registration of the key it was obtained for. The ID token
 * comes from the hybrid flow (response_type=code id_token), which needs no client authentication:
 * the client has no instance to authenticate with until the registration is done.
 */
import crypto from "crypto";
import { v4 as uuidv4 } from "uuid";
import { postWithJson } from "../http";
import { requestAuthorizations } from "../../oauth/request";
import { backendUrl } from "../../tests/testConfig";

const base64url = (buffer) =>
  buffer.toString("base64").replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const base64urlDecode = (value) =>
  Buffer.from(value.replace(/-/g, "+").replace(/_/g, "/"), "base64");

/** RFC 7638 thumbprint input: required members only, lexicographic, no whitespace. */
export const canonicalJwk = (jwk) => {
  switch (jwk.kty) {
    case "EC":
      return JSON.stringify({ crv: jwk.crv, kty: jwk.kty, x: jwk.x, y: jwk.y });
    case "RSA":
      return JSON.stringify({ e: jwk.e, kty: jwk.kty, n: jwk.n });
    default:
      throw new Error(`unsupported kty for canonical JWK: ${jwk.kty}`);
  }
};

export const deriveRequestHash = (challenge, jwk) => {
  const digest = crypto.createHash("sha256");
  digest.update(base64urlDecode(challenge));
  digest.update(Buffer.from(canonicalJwk(jwk), "utf8"));
  return base64url(digest.digest());
};

/**
 * Lets users of an onboarded tenant log in with a password at the authorization endpoint: a
 * password authentication configuration, and an OAuth authentication policy that accepts it.
 */
export const enablePasswordLogin = async ({ tenantId, headers }) => {
  const managementBase = `${backendUrl}/v1/management/tenants/${tenantId}`;

  const configurationResponse = await postWithJson({
    url: `${managementBase}/authentication-configurations`,
    headers,
    body: {
      id: uuidv4(),
      type: "password",
      attributes: {},
      metadata: { type: "password" },
      interactions: {
        "password-authentication": {
          request: {
            schema: {
              type: "object",
              properties: { username: { type: "string" }, password: { type: "string" } },
              required: ["username", "password"],
            },
          },
          pre_hook: {},
          execution: { function: "password_verification" },
          post_hook: {},
          response: {
            body_mapping_rules: [
              { from: "$.user_id", to: "user_id" },
              { from: "$.error", to: "error" },
            ],
          },
        },
      },
    },
  });
  if (configurationResponse.status !== 201) {
    throw new Error(
      `password authentication configuration failed: ${configurationResponse.status} ${JSON.stringify(configurationResponse.data)}`
    );
  }

  const policyResponse = await postWithJson({
    url: `${managementBase}/authentication-policies`,
    headers,
    body: {
      id: uuidv4(),
      flow: "oauth",
      enabled: true,
      policies: [
        {
          description: "password",
          priority: 1,
          conditions: {},
          available_methods: ["password"],
          success_conditions: {
            any_of: [
              [
                {
                  path: "$.password-authentication.success_count",
                  type: "integer",
                  operation: "gte",
                  value: 1,
                },
              ],
            ],
          },
        },
      ],
    },
  });
  if (policyResponse.status !== 201) {
    throw new Error(
      `authentication policy failed: ${policyResponse.status} ${JSON.stringify(policyResponse.data)}`
    );
  }
};

/**
 * Logs in with a password in the hybrid flow and returns the code and the ID token of the
 * authorization response. The nonce is what binds the ID token to the key; pass the request hash
 * of the challenge and the key about to be registered.
 */
export const loginForRegistration = async ({
  tenantId,
  clientId,
  redirectUri,
  nonce,
  username,
  password,
  scope = "openid",
}) => {
  const tenantBase = `${backendUrl}/${tenantId}`;

  const { authorizationResponse, status, error } = await requestAuthorizations({
    endpoint: `${tenantBase}/v1/authorizations`,
    authorizeEndpoint: `${tenantBase}/v1/authorizations/{id}/authorize`,
    clientId,
    responseType: "code id_token",
    scope,
    redirectUri,
    state: uuidv4(),
    nonce,
    user: { username, password },
    interaction: async (id, user) => {
      const response = await postWithJson({
        url: `${tenantBase}/v1/authorizations/${id}/password-authentication`,
        body: user,
      });
      if (response.status !== 200) {
        throw new Error(
          `password authentication failed: ${response.status} ${JSON.stringify(response.data)}`
        );
      }
    },
  });

  if (!authorizationResponse?.idToken || !authorizationResponse?.code) {
    throw new Error(
      `hybrid login returned no code / id_token: ${status} ${JSON.stringify(error ?? authorizationResponse)}`
    );
  }
  return { code: authorizationResponse.code, idToken: authorizationResponse.idToken };
};
