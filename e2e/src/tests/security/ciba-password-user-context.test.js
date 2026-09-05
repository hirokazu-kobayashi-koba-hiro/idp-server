import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import {
  requestBackchannelAuthentications,
  requestToken,
} from "../../api/oauthClient";
import {
  backendUrl,
  clientSecretPostClient,
  mockApiBaseUrl,
  serverConfig,
} from "../testConfig";
import { postWithJson, get, deletion } from "../../lib/http";
import { faker } from "@faker-js/faker";
import { v4 as uuidv4 } from "uuid";
import { generateRS256KeyPair } from "../../lib/jose";

/**
 * Issue #1862: the counterpart of password-authentication-user-context.test.js for CIBA.
 *
 * `$.user` is gated on AuthenticationTransaction#hasTrustedUser(), which is true for a CIBA
 * transaction from the very first interaction: the backchannel request is client-authenticated
 * before the transaction exists, so the client vouches for the user it named. This is the
 * behaviour documented in developer-guide 05-configuration/authn/external-api.md and it must keep
 * working for a step that does NOT declare requires_user — a plain requires_user gate would kill
 * it.
 *
 * Prerequisites: Mockoon at host.docker.internal:4000 (POST /e2e/echo-user-context reflects the
 * received body fields back).
 */
describe("Security: CIBA password execution receives the user projection from the 1st interaction (#1862)", () => {
  let adminAccessToken;
  let tenantId;
  let clientId;
  let clientSecret;
  let userEmail;
  const testPassword = "CibaUserContext123!";
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
          name: "CIBA user context Tenant",
          domain: backendUrl,
          authorization_provider: "idp-server",
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          backchannel_authentication_endpoint: `${backendUrl}/${tenantId}/v1/backchannel/authentications`,
          jwks_uri: `${backendUrl}/${tenantId}/.well-known/jwks.json`,
          jwks: jwks,
          scopes_supported: ["openid", "profile", "email"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          grant_types_supported: [
            "authorization_code",
            "urn:openid:params:grant-type:ciba",
          ],
          backchannel_token_delivery_modes_supported: ["poll"],
          backchannel_user_code_parameter_supported: false,
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
        redirect_uris: [`${backendUrl}/callback`],
        grant_types: ["urn:openid:params:grant-type:ciba"],
        response_types: ["code"],
        scope: scope,
        token_endpoint_auth_method: "client_secret_post",
        backchannel_token_delivery_mode: "poll",
        backchannel_user_code_parameter: false,
      },
    });
    expect(createClientResponse.status).toBe(201);

    // The password execution maps ONLY $.user.* into the external request. No user_resolve: on a
    // step without requires_user the interactor would otherwise resolve identity from the external
    // response instead of the database.
    const createPasswordConfigResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: uuidv4(),
        type: "password",
        attributes: {},
        interactions: {
          "password-authentication": {
            execution: {
              function: "http_request",
              http_request: {
                url: `${mockApiBaseUrl}/e2e/echo-user-context`,
                method: "POST",
                header_mapping_rules: [
                  { static_value: "application/json", to: "Content-Type" },
                ],
                body_mapping_rules: [
                  { from: "$.user.email", to: "email" },
                  { from: "$.user.sub", to: "user_id" },
                ],
              },
            },
            response: {
              body_mapping_rules: [
                {
                  from: "$.execution_http_request.response_body.received_email",
                  to: "echoed_email",
                },
                {
                  from: "$.execution_http_request.response_body.received_sub",
                  to: "echoed_sub",
                },
              ],
            },
          },
        },
      },
    });
    expect(createPasswordConfigResponse.status).toBe(201);

    // password is the ONLY step and does not declare requires_user. Under a requires_user gate the
    // projection would be empty here; under hasTrustedUser() the client-authenticated CIBA request
    // is what makes the user projectable.
    const createPolicyResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: uuidv4(),
        flow: "ciba",
        enabled: true,
        policies: [
          {
            description: "ciba_password_only",
            priority: 100,
            conditions: { scopes: ["openid"] },
            available_methods: ["password"],
            step_definitions: [
              {
                method: "password",
                order: 1,
                requires_user: false,
                allow_registration: false,
              },
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
    expect(createPolicyResponse.status).toBe(201);

    userEmail = faker.internet.email().toLowerCase();
    const createUserResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/users`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        sub: uuidv4(),
        provider_id: "idp-server",
        name: "Ciba User Context",
        email: userEmail,
        preferred_username: userEmail,
        raw_password: testPassword,
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

  it("should forward $.user to the password execution on the first CIBA interaction", async () => {
    const backchannelResponse = await requestBackchannelAuthentications({
      endpoint: `${backendUrl}/${tenantId}/v1/backchannel/authentications`,
      scope: scope,
      loginHint: `email:${userEmail}`,
      clientId: clientId,
      clientSecret: clientSecret,
    });
    expect(backchannelResponse.status).toBe(200);
    const authReqId = backchannelResponse.data.auth_req_id;
    expect(authReqId).toBeDefined();

    const txResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/authentication-transactions?attributes.auth_req_id=${authReqId}`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
    });
    expect(txResponse.status).toBe(200);
    const transactionId = txResponse.data.list[0].id;

    // The very first interaction of the transaction: nothing has been verified yet.
    const passwordResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authentications/${transactionId}/password-authentication`,
      body: { username: userEmail, password: testPassword },
    });
    expect(passwordResponse.status).toBe(200);

    // The client authenticated at the backchannel endpoint before naming this user, so the
    // projection is sent even though no factor has succeeded in this transaction yet.
    expect(passwordResponse.data.echoed_email).toBe(userEmail);
    expect(passwordResponse.data.echoed_sub).toBeTruthy();
  }, 90000);
});
