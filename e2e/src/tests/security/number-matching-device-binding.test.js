import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { getAuthorizations, requestToken } from "../../api/oauthClient";
import {
  backendUrl,
  clientSecretPostClient,
  serverConfig,
} from "../testConfig";
import { postWithJson, deletion } from "../../lib/http";
import { faker } from "@faker-js/faker";
import { v4 as uuidv4 } from "uuid";
import { generateRS256KeyPair } from "../../lib/jose";
import { convertNextAction } from "../../lib/util";

/**
 * Issue #1869: the number-matching code is never sent to the authentication device — it is shown on
 * the sign-in screen and the user transcribes it into the device. That only proves the approver saw
 * the originating screen if the submission actually comes from the device the transaction is about,
 * so the verification is bound to the transaction's authentication device.
 *
 * The code is in the challenge response, so whoever drives the flow always has it. These tests
 * therefore assert on the device binding, not on knowledge of the code.
 */
describe("Security: number-matching is bound to the transaction's authentication device (#1869)", () => {
  let adminAccessToken;
  let tenantId;
  let clientId;
  let clientSecret;
  let userEmail;
  const boundDeviceId = uuidv4();
  const otherDeviceId = uuidv4();
  const testPassword = "NumberMatching123!";
  const redirectUri = "http://localhost:8080/callback";
  const scope = "openid profile email";

  beforeAll(async () => {
    const adminTokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      grantType: "password",
      username: serverConfig.oauth.username,
      password: serverConfig.oauth.password,
      scope: clientSecretPostClient.scope,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });
    expect(adminTokenResponse.status).toBe(200);
    adminAccessToken = adminTokenResponse.data.access_token;

    tenantId = uuidv4();
    const { jwks } = await generateRS256KeyPair();
    const createTenantResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        tenant: {
          id: tenantId,
          name: "Number matching device binding Tenant",
          domain: backendUrl,
          authorization_provider: "idp-server",
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/.well-known/jwks.json`,
          jwks: jwks,
          scopes_supported: ["openid", "profile", "email"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          grant_types_supported: ["authorization_code"],
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          id_token_signing_alg_values_supported: ["RS256"],
          claims_supported: [
            "sub",
            "name",
            "email",
            "email_verified",
            "preferred_username",
          ],
          extension: {
            access_token_type: "JWT",
            access_token_duration: 3600,
            id_token_duration: 3600,
          },
        },
      },
    });
    expect(createTenantResponse.status).toBe(201);

    clientId = uuidv4();
    clientSecret = uuidv4();
    const createClientResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/clients`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        client_id: clientId,
        client_secret: clientSecret,
        redirect_uris: [redirectUri],
        grant_types: ["authorization_code"],
        response_types: ["code"],
        scope: scope,
        token_endpoint_auth_method: "client_secret_post",
      },
    });
    expect(createClientResponse.status).toBe(201);

    // password (1st factor) then number-matching (2nd). The 1st factor is what binds the user, and
    // AuthenticationRequest#updateWithUser then resolves that user's primary authentication device
    // into the transaction — which is what number-matching compares against.
    const createPolicyResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: uuidv4(),
        flow: "oauth",
        enabled: true,
        policies: [
          {
            description: "password_then_number_matching",
            priority: 100,
            conditions: { scopes: ["openid"] },
            available_methods: [
              "password",
              "authentication-device-number-matching-challenge",
              "authentication-device-number-matching",
            ],
            step_definitions: [
              {
                method: "password",
                order: 1,
                requires_user: false,
                allow_registration: false,
              },
              { method: "number-matching", order: 2, requires_user: true },
            ],
            success_conditions: {
              any_of: [
                [
                  {
                    path: "$.authentication-device-number-matching.success_count",
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
    expect(createPolicyResponse.status).toBe(201);

    userEmail = faker.internet.email().toLowerCase();
    const createUserResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/users`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        sub: uuidv4(),
        provider_id: "idp-server",
        name: "Number Matching User",
        email: userEmail,
        preferred_username: userEmail,
        raw_password: testPassword,
        // The management API deserializes the body straight into User, so the device is registered
        // here rather than through a FIDO-UAF registration flow.
        authentication_devices: [
          {
            id: boundDeviceId,
            app_name: "bound-app",
            platform: "Android",
            os: "14",
            model: "Pixel",
            locale: "ja",
            // Both are required by the user registration schema.
            notification_channel: "fcm",
            notification_token: "e2e-dummy-token",
            available_methods: ["number-matching"],
            priority: 1,
          },
        ],
      },
    });
    expect(createUserResponse.status).toBe(201);
  }, 60000);

  afterAll(async () => {
    if (!adminAccessToken || !tenantId) {
      return;
    }
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
    }).catch(() => {});
  });

  /** Runs the 1st factor and issues a code, returning the transaction id and the issued code. */
  const startFlowAndIssueCode = async () => {
    const authResponse = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId: clientId,
      responseType: "code",
      state: `nm-${Date.now()}-${Math.random()}`,
      scope: scope,
      redirectUri: redirectUri,
    });
    expect(authResponse.status).toBe(302);
    const { params } = convertNextAction(authResponse.headers.location);
    const authId = params.get("id");

    const passwordResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/password-authentication`,
      body: { username: userEmail, password: testPassword },
    });
    expect(passwordResponse.status).toBe(200);

    const challengeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/authentication-device-number-matching-challenge`,
      body: {},
    });
    expect(challengeResponse.status).toBe(200);
    // The code is handed to the sign-in screen by design; the caller always has it.
    const code = challengeResponse.data.number_matching_code;
    expect(code).toBeTruthy();

    return { authId, code };
  };

  it("should accept the code from the bound authentication device", async () => {
    const { authId, code } = await startFlowAndIssueCode();

    const response = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/authentication-device-number-matching`,
      body: { device_id: boundDeviceId, number_matching_code: code },
    });

    expect(response.status).toBe(200);
  }, 90000);

  it("should reject the correct code submitted from another device", async () => {
    const { authId, code } = await startFlowAndIssueCode();

    const response = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/authentication-device-number-matching`,
      body: { device_id: otherDeviceId, number_matching_code: code },
    });

    expect(response.status).toBe(400);
    expect(response.data.error_description).toBe(
      "device_id does not match the authentication device"
    );
  }, 90000);

  it("should reject a submission without device_id", async () => {
    const { authId, code } = await startFlowAndIssueCode();

    const response = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/authentication-device-number-matching`,
      body: { number_matching_code: code },
    });

    expect(response.status).toBe(400);
  }, 90000);

  it("should still reject a wrong code from the bound device", async () => {
    const { authId } = await startFlowAndIssueCode();

    const response = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/authentication-device-number-matching`,
      body: { device_id: boundDeviceId, number_matching_code: "000000" },
    });

    expect(response.status).toBe(400);
    expect(response.data.error_description).toBe(
      "number_matching_code does not match"
    );
  }, 90000);
});
