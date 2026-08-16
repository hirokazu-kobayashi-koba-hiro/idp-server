import { describe, expect, it } from "@jest/globals";
import { requestToken } from "../../../api/oauthClient";
import { backendUrl, clientSecretPostClient, serverConfig } from "../../testConfig";
import { postWithJson, get } from "../../../lib/http";
import { faker } from "@faker-js/faker";
import { v4 as uuidv4 } from "uuid";
import { requestAuthorizations } from "../../../oauth/request";
import { generateRS256KeyPair } from "../../../lib/jose";

/**
 * Issue #1772: custom_properties must merge key by key across authentication methods.
 *
 * custom_properties is a flat key set that several writers contribute to — federation,
 * external-api authentication, a 2nd factor, identity verification. Each writer only declares the
 * keys it produces, so replacing the whole map means whichever method ran last silently drops the
 * keys the others had put there. The failure is quiet: nothing errors, the attribute is simply
 * gone the next time something reads it.
 *
 * Here a key is seeded on the user first (standing in for whatever another method wrote), then a
 * login runs whose 2nd-factor user_resolve declares a different key. Both have to survive.
 */
describe("Authentication: custom_properties merge across methods (#1772)", () => {
  it("keeps keys written by another method when user_resolve declares its own", async () => {
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
    const adminAccessToken = adminTokenResponse.data.access_token;

    const orgId = serverConfig.organizationId;
    const tenantId = uuidv4();
    const { jwks } = await generateRS256KeyPair();

    const createTenantResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        tenant: {
          id: tenantId,
          name: "custom_properties merge Tenant",
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
          scopes_supported: ["openid", "profile", "email", "management"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          grant_types_supported: ["authorization_code", "password"],
          id_token_signing_alg_values_supported: ["RS256"],
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          claims_supported: ["sub", "name", "email", "email_verified", "preferred_username"],
          extension: {
            access_token_type: "JWT",
            access_token_duration: 3600,
            id_token_duration: 3600,
          },
        },
      },
    });
    expect(createTenantResponse.status).toBe(201);

    const clientId = uuidv4();
    const clientSecret = uuidv4();
    const redirectUri = "https://app.example.com/callback";
    const createClientResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/clients`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        client_id: clientId,
        client_secret: clientSecret,
        client_name: "custom_properties merge client",
        redirect_uris: [redirectUri],
        grant_types: ["authorization_code", "password"],
        response_types: ["code"],
        scope: "openid profile email",
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
      },
    });
    expect(createClientResponse.status).toBe(201);

    // The 2nd factor declares only its own key. It has no way to know about SEEDED_KEY — that is
    // precisely why it must not replace the whole map.
    const SEEDED_KEY = "attr_from_other_method";
    const RESOLVED_KEY = "attr_from_password_resolve";
    // Declared by this method's rules but whose source is absent, so the rule produces null.
    const UNPRODUCED_KEY = "attr_declared_but_unproduced";

    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: uuidv4(),
        type: "password",
        attributes: {},
        metadata: { type: "internal", description: "password with user_resolve" },
        interactions: {
          "password-authentication": {
            request: {
              schema: {
                type: "object",
                properties: { username: { type: "string" }, password: { type: "string" } },
              },
            },
            execution: { function: "password_verification" },
            user_resolve: {
              user_mapping_rules: [
                { static_value: "resolved_by_password", to: `custom_properties.${RESOLVED_KEY}` },
                // The source is never present, so this rule resolves to null. A null must not
                // erase the value already on the user.
                { from: "$.request_body.never_present", to: `custom_properties.${UNPRODUCED_KEY}` },
              ],
            },
            response: { body_mapping_rules: [] },
          },
        },
      },
    });

    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: uuidv4(),
        type: "email",
        attributes: {},
        metadata: {
          type: "external",
          transaction_id_param: "transaction_id",
          verification_code_param: "verification_code",
        },
        interactions: {
          "email-authentication-challenge": {
            request: { schema: { type: "object", properties: { email: { type: "string" } } } },
            execution: {
              function: "email_authentication_challenge",
              details: {
                function: "no_action",
                sender: "test@gmail.com",
                templates: {
                  registration: { subject: "Verification Code", body: "Code: {VERIFICATION_CODE}" },
                  authentication: { subject: "Verification Code", body: "Code: {VERIFICATION_CODE}" },
                },
                retry_count_limitation: 5,
                expire_seconds: 300,
              },
            },
            response: { body_mapping_rules: [{ from: "$.response_body", to: "*" }] },
          },
          "email-authentication": {
            request: {
              schema: { type: "object", properties: { verification_code: { type: "string" } } },
            },
            execution: { function: "email_authentication" },
            response: { body_mapping_rules: [{ from: "$.response_body", to: "*" }] },
          },
        },
      },
    });

    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: uuidv4(),
        flow: "oauth",
        enabled: true,
        policies: [
          {
            description: "email_then_password",
            priority: 100,
            conditions: { scopes: ["openid"] },
            available_methods: ["email", "password", "initial-registration"],
            step_definitions: [
              {
                method: "email",
                order: 1,
                requires_user: false,
                allow_registration: true,
                user_identity_source: "email",
              },
              { method: "password", order: 2, requires_user: true, allow_registration: true },
            ],
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

    // Seed the user with a key that no configured mapping rule declares.
    const userEmail = faker.internet.email();
    const userPassword = "MergeTestPass123!";
    const createUserResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        sub: uuidv4(),
        provider_id: "idp-server",
        email: userEmail,
        email_verified: true,
        raw_password: userPassword,
        custom_properties: {
          [SEEDED_KEY]: "written_by_another_method",
          [UNPRODUCED_KEY]: "must_survive_a_null_producing_rule",
        },
      },
    });
    expect(createUserResponse.status).toBe(201);
    const userId = createUserResponse.data.result.sub;

    const interaction = async (id) => {
      const challengeResponse = await postWithJson({
        url: `${backendUrl}/${tenantId}/v1/authorizations/${id}/email-authentication-challenge`,
        body: { email: userEmail, template: "authentication" },
      });
      expect(challengeResponse.status).toBe(200);

      const txResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-transactions?authorization_id=${id}`,
        headers: { Authorization: `Bearer ${adminAccessToken}` },
      });
      const transactionId = txResponse.data.list[0].id;
      const interactionResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-interactions/${transactionId}/email-authentication-challenge`,
        headers: { Authorization: `Bearer ${adminAccessToken}` },
      });

      const emailVerifyResponse = await postWithJson({
        url: `${backendUrl}/${tenantId}/v1/authorizations/${id}/email-authentication`,
        body: { verification_code: interactionResponse.data.payload.verification_code },
      });
      expect(emailVerifyResponse.status).toBe(200);

      const passwordResponse = await postWithJson({
        url: `${backendUrl}/${tenantId}/v1/authorizations/${id}/password-authentication`,
        body: { username: userEmail, password: userPassword },
      });
      expect(passwordResponse.status).toBe(200);
    };

    const { authorizationResponse } = await requestAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      authorizeEndpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      denyEndpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/deny`,
      clientId: clientId,
      responseType: "code",
      state: `state_${Date.now()}`,
      scope: "openid profile email",
      redirectUri: redirectUri,
      user: { email: userEmail, password: userPassword },
      interaction,
    });
    expect(authorizationResponse.code).not.toBeNull();

    const getUserResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
    });
    expect(getUserResponse.status).toBe(200);
    console.log(
      "custom_properties after login:",
      JSON.stringify(getUserResponse.data.custom_properties, null, 2)
    );

    // The key the login resolved is applied...
    expect(getUserResponse.data.custom_properties[RESOLVED_KEY]).toBe("resolved_by_password");
    // ...and the key it never declared is still there. Before #1772 this was dropped.
    expect(getUserResponse.data.custom_properties[SEEDED_KEY]).toBe("written_by_another_method");

    // A rule that resolved to null must leave the existing value alone rather than nulling it.
    expect(getUserResponse.data.custom_properties[UNPRODUCED_KEY]).toBe(
      "must_survive_a_null_producing_rule"
    );
  }, 90000);
});
