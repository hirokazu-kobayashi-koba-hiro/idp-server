import { describe, expect, it, beforeAll } from "@jest/globals";
import { get, postWithJson } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { onboarding } from "../../../api/managementClient";
import { generateRS256KeyPair } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import { faker } from "@faker-js/faker";

/**
 * Use Case: FIDO-UAF authentication verifies the authenticated device with post_hook (#1973)
 *
 * The mock FIDO server (fido-uaf/authentication-device-check) reports the authenticated device as
 * user_id, echoing authenticated_device_id from the request. The tenant configures a post_hook
 * match rule that compares it with the device_id of the challenge.
 */
describe("Use Case: FIDO-UAF authenticated device check with post_hook", () => {
  const mockUrl = "http://host.docker.internal:4000";
  const redirectUri = "https://app.example.com/callback";
  const timestamp = Date.now();
  const organizationId = uuidv4();
  const tenantId = uuidv4();
  const clientId = uuidv4();
  const clientSecret = `client-secret-${timestamp}`;

  let owner;
  let other;

  const fidoUafInteraction = (url, extra = {}) => ({
    request: { schema: { type: "object", properties: {} } },
    pre_hook: {},
    execution: {
      function: "http_request",
      http_request: {
        url,
        method: "POST",
        header_mapping_rules: [{ static_value: "application/json", to: "Content-Type" }],
        body_mapping_rules: [{ from: "$.request_body", to: "*" }],
      },
      ...extra.execution,
    },
    post_hook: extra.post_hook || {},
    response: {
      body_mapping_rules: [{ from: "$.execution_http_request.response_body", to: "*" }],
    },
  });

  const startAuthorization = async () => {
    const params = new URLSearchParams({
      response_type: "code",
      client_id: clientId,
      redirect_uri: redirectUri,
      scope: "openid profile email",
      state: `state_${Date.now()}`,
    });
    const response = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${params.toString()}`,
    });
    expect(response.status).toBe(302);
    return new URL(response.headers.location, backendUrl).searchParams.get("id");
  };

  const interact = (authId, interactionType, body = {}) =>
    postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/${interactionType}`,
      body,
    });

  /** Registers a user and a FIDO-UAF device, and returns the device_id. */
  const registerUserWithDevice = async () => {
    const authId = await startAuthorization();
    const registration = await interact(authId, "initial-registration", {
      email: faker.internet.email(),
      password: `Password${Date.now()}!`,
      name: faker.person.fullName(),
    });
    expect(registration.status).toBe(200);

    const challenge = await interact(authId, "fido-uaf-registration-challenge");
    expect(challenge.status).toBe(200);
    const device = await interact(authId, "fido-uaf-registration");
    expect(device.status).toBe(200);
    expect(device.data.device_id).toBeDefined();

    // The device is stored with the user when the authorization completes
    const authorize = await interact(authId, "authorize");
    expect(authorize.status).toBe(200);
    return { sub: registration.data.user.sub, deviceId: device.data.device_id };
  };

  /** Runs the FIDO-UAF authentication with the challenge for challengeDeviceId. */
  const authenticate = async ({ challengeDeviceId, authenticatedDeviceId }) => {
    const authId = await startAuthorization();
    const challenge = await interact(authId, "fido-uaf-authentication-challenge", {
      device_id: challengeDeviceId,
    });
    expect(challenge.status).toBe(200);

    const body = authenticatedDeviceId ? { authenticated_device_id: authenticatedDeviceId } : {};
    return interact(authId, "fido-uaf-authentication", body);
  };

  beforeAll(async () => {
    const systemToken = await requestToken({
      endpoint: adminServerConfig.tokenEndpoint,
      grantType: "password",
      username: adminServerConfig.oauth.username,
      password: adminServerConfig.oauth.password,
      scope: adminServerConfig.adminClient.scope,
      clientId: adminServerConfig.adminClient.clientId,
      clientSecret: adminServerConfig.adminClient.clientSecret,
    });
    expect(systemToken.status).toBe(200);

    const { jwks } = await generateRS256KeyPair();
    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `FIDO-UAF Device Check Org ${timestamp}`,
        description: "Test organization for FIDO-UAF authenticated device check",
      },
      tenant: {
        id: tenantId,
        name: `FIDO-UAF Device Check Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
      },
      authorization_server: {
        issuer: `${backendUrl}/${tenantId}`,
        authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
        token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        token_endpoint_auth_methods_supported: ["client_secret_post", "client_secret_basic"],
        token_endpoint_auth_signing_alg_values_supported: ["RS256", "ES256"],
        userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
        jwks: jwks,
        grant_types_supported: ["authorization_code", "refresh_token", "password"],
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        scopes_supported: ["openid", "profile", "email", "management"],
        response_types_supported: ["code"],
        response_modes_supported: ["query", "fragment"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["RS256", "ES256"],
        claims_parameter_supported: true,
        extension: {
          access_token_type: "JWT",
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          access_token_duration: 3600,
          id_token_duration: 3600,
          refresh_token_duration: 86400,
        },
      },
      user: {
        sub: uuidv4(),
        provider_id: "idp-server",
        name: faker.internet.email(),
        email: faker.internet.email(),
        email_verified: true,
        raw_password: `AdminPass${timestamp}!`,
      },
      client: {
        client_id: uuidv4(),
        client_id_alias: `admin-client-device-check-${timestamp}`,
        client_secret: `admin-secret-${timestamp}`,
        redirect_uris: [redirectUri],
        response_types: ["code"],
        grant_types: ["authorization_code", "refresh_token", "password"],
        scope: "openid profile email management",
        client_name: "Admin Client",
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
      },
    };
    const onboardingResponse = await onboarding({
      headers: { Authorization: `Bearer ${systemToken.data.access_token}` },
      body: onboardingRequest,
    });
    expect(onboardingResponse.status).toBe(201);

    const adminToken = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: onboardingRequest.user.email,
      password: onboardingRequest.user.raw_password,
      scope: "management",
      clientId: onboardingResponse.data.client.client_id,
      clientSecret: onboardingResponse.data.client.client_secret,
    });
    expect(adminToken.status).toBe(200);
    const management = (path, body) =>
      postWithJson({
        url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/${path}`,
        headers: { Authorization: `Bearer ${adminToken.data.access_token}` },
        body,
      });

    const fidoUafConfig = await management("authentication-configurations", {
      id: uuidv4(),
      type: "fido-uaf",
      attributes: {},
      metadata: { type: "external", description: "FIDO-UAF with authenticated device check" },
      interactions: {
        "fido-uaf-registration-challenge": fidoUafInteraction(
          `${mockUrl}/fido-uaf/registration-challenge`,
          {
            execution: {
              http_request_store: {
                key: "fido-uaf-registration-challenge",
                interaction_mapping_rules: [{ from: "$.response_body.challenge", to: "challenge" }],
              },
            },
          }
        ),
        "fido-uaf-registration": fidoUafInteraction(`${mockUrl}/fido-uaf/registration`, {
          execution: { previous_interaction: { key: "fido-uaf-registration-challenge" } },
        }),
        "fido-uaf-authentication-challenge": fidoUafInteraction(
          `${mockUrl}/fido-uaf/authentication-challenge`
        ),
        "fido-uaf-authentication": fidoUafInteraction(
          `${mockUrl}/fido-uaf/authentication-device-check`,
          {
            post_hook: {
              verifications: [
                {
                  type: "match",
                  details: {
                    rules: [
                      {
                        path: "$.execution_http_request.response_body.user_id",
                        operation: "eq",
                        target_path: "$.interaction.device_id",
                      },
                    ],
                  },
                },
              ],
            },
          }
        ),
      },
    });
    expect(fidoUafConfig.status).toBe(201);

    const policy = await management("authentication-policies", {
      id: uuidv4(),
      flow: "oauth",
      enabled: true,
      policies: [
        {
          description: "fido_uaf_authenticated_device_check",
          priority: 10,
          conditions: { scopes: ["openid"] },
          available_methods: ["initial-registration", "fido-uaf"],
          success_conditions: {
            any_of: [
              [{ path: "$.initial-registration.success_count", type: "integer", operation: "gte", value: 1 }],
              [{ path: "$.fido-uaf-authentication.success_count", type: "integer", operation: "gte", value: 1 }],
            ],
          },
        },
      ],
    });
    expect(policy.status).toBe(201);

    const client = await management("clients", {
      client_id: clientId,
      client_secret: clientSecret,
      client_name: "FIDO-UAF Device Check Client",
      redirect_uris: [redirectUri],
      grant_types: ["authorization_code", "password"],
      response_types: ["code"],
      scope: "openid profile email",
      token_endpoint_auth_method: "client_secret_post",
    });
    expect(client.status).toBe(201);

    owner = await registerUserWithDevice();
    other = await registerUserWithDevice();
  });

  it("succeeds when the FIDO server authenticated the device of the challenge", async () => {
    const response = await authenticate({
      challengeDeviceId: owner.deviceId,
      authenticatedDeviceId: owner.deviceId,
    });

    expect(response.status).toBe(200);
  });

  it("rejects when the FIDO server authenticated another device", async () => {
    const response = await authenticate({
      challengeDeviceId: owner.deviceId,
      authenticatedDeviceId: other.deviceId,
    });

    expect(response.status).toBe(400);
    expect(response.data.error).toBe("invalid_request");
    expect(response.data.error_description).toBe(
      "FIDO-UAF authentication result did not pass the verification."
    );
  });

  it("rejects when the FIDO server does not report the authenticated device", async () => {
    const response = await authenticate({ challengeDeviceId: owner.deviceId });

    expect(response.status).toBe(400);
    expect(response.data.error).toBe("invalid_request");
    expect(response.data.error_description).toBe(
      "FIDO-UAF authentication result did not pass the verification."
    );
  });
});
