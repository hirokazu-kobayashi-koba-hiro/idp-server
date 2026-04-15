import { describe, expect, it, beforeAll } from "@jest/globals";
import { onboarding } from "../../../api/managementClient";
import { postWithJson } from "../../../lib/http";
import {
  requestToken,
  getJwks,
  requestBackchannelAuthentications,
  getAuthenticationDeviceAuthenticationTransaction,
  postAuthenticationDeviceInteraction,
} from "../../../api/oauthClient";
import { generateECP256JWKS, verifyAndDecodeJwt } from "../../../lib/jose";
import { isNumber, isArray } from "../../../lib/util";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";

/**
 * CIBA Use Case: Custom Claims in ID Token
 *
 * Verifies that custom claims via claims: scope prefix are correctly
 * included in ID tokens issued through the CIBA flow.
 *
 * Scenarios:
 * - claims:ex_sub → external_user_id in CIBA ID token
 * - claims:status → user status in CIBA ID token
 * - claims:roles / claims:permissions → RBAC claims in CIBA ID token
 * - claims:{key} → custom_properties values in CIBA ID token
 */
describe("CIBA Use Case: Custom Claims in ID Token", () => {
  let systemAccessToken;
  let tenantId;
  let organizationId;
  let deviceId;
  let cibaClientId;
  let cibaClientSecret;
  let userEmail;
  let userPassword;
  let userSub;
  let mgmtToken;
  const externalUserId = "CIBA-EXT-001";
  const employeeNumber = "EMP-CIBA-777";

  beforeAll(async () => {
    const adminTokenResponse = await requestToken({
      endpoint: adminServerConfig.tokenEndpoint,
      grantType: "password",
      username: adminServerConfig.oauth.username,
      password: adminServerConfig.oauth.password,
      scope: adminServerConfig.adminClient.scope,
      clientId: adminServerConfig.adminClient.clientId,
      clientSecret: adminServerConfig.adminClient.clientSecret,
    });
    expect(adminTokenResponse.status).toBe(200);
    systemAccessToken = adminTokenResponse.data.access_token;

    // Setup tenant with CIBA + custom claims
    const timestamp = Date.now();
    organizationId = uuidv4();
    tenantId = uuidv4();
    userSub = uuidv4();
    deviceId = uuidv4();
    const mgmtClientId = uuidv4();
    const mgmtClientSecret = `mgmt-${crypto.randomBytes(16).toString("hex")}`;
    cibaClientId = uuidv4();
    cibaClientSecret = `ciba-${crypto.randomBytes(16).toString("hex")}`;
    userEmail = `ciba-claims-${timestamp}@test.example.com`;
    userPassword = `CibaClaims${timestamp}!`;
    const jwksContent = await generateECP256JWKS();

    const onboardingResponse = await onboarding({
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: {
        organization: {
          id: organizationId,
          name: `CIBA Claims Org ${timestamp}`,
          description: "CIBA custom claims test",
        },
        tenant: {
          id: tenantId,
          name: `CIBA Claims Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: { identity_unique_key_type: "EMAIL" },
          session_config: {
            cookie_name: `CIBA_CL_${tenantId.substring(0, 8)}`,
            use_secure_cookie: false,
          },
          cors_config: { allow_origins: [backendUrl] },
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
          jwks: jwksContent,
          grant_types_supported: [
            "authorization_code",
            "password",
            "urn:openid:params:grant-type:ciba",
          ],
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          scopes_supported: [
            "openid", "profile", "email", "management",
            "claims:ex_sub", "claims:status",
            "claims:roles", "claims:permissions",
            "claims:employee_number",
          ],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          id_token_signing_alg_values_supported: ["ES256"],
          backchannel_authentication_endpoint: `${backendUrl}/${tenantId}/v1/backchannel/authentications`,
          backchannel_token_delivery_modes_supported: ["poll"],
          backchannel_user_code_parameter_supported: false,
          extension: {
            access_token_type: "JWT",
            backchannel_authentication_polling_interval: 3,
            backchannel_authentication_request_expires_in: 120,
            required_backchannel_auth_user_code: false,
            custom_claims_scope_mapping: true,
          },
        },
        user: {
          sub: userSub,
          provider_id: "idp-server",
          external_user_id: externalUserId,
          email: userEmail,
          email_verified: true,
          raw_password: userPassword,
          authentication_devices: [{ id: deviceId, app_name: "CIBA Claims Test" }],
          custom_properties: {
            employee_number: employeeNumber,
          },
        },
        client: {
          client_id: mgmtClientId,
          client_secret: mgmtClientSecret,
          redirect_uris: ["http://localhost:3000/callback"],
          response_types: ["code"],
          grant_types: ["authorization_code", "password"],
          scope: "openid profile email management",
          client_name: "Mgmt Client",
          token_endpoint_auth_method: "client_secret_post",
          application_type: "web",
        },
      },
    });
    expect(onboardingResponse.status).toBe(201);

    // Get management token to create CIBA client
    const mgmtResp = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: userEmail,
      password: userPassword,
      scope: "management",
      clientId: mgmtClientId,
      clientSecret: mgmtClientSecret,
    });
    expect(mgmtResp.status).toBe(200);
    mgmtToken = mgmtResp.data.access_token;

    // Create CIBA client with custom claims scopes
    const cibaClientResp = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients`,
      headers: { Authorization: `Bearer ${mgmtToken}` },
      body: {
        client_id: cibaClientId,
        client_secret: cibaClientSecret,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: ["urn:openid:params:grant-type:ciba"],
        scope: "openid profile email claims:ex_sub claims:status claims:roles claims:permissions claims:employee_number",
        client_name: "CIBA Custom Claims Client",
        token_endpoint_auth_method: "client_secret_post",
        backchannel_token_delivery_mode: "poll",
        backchannel_user_code_parameter: false,
        extension: {
          default_ciba_authentication_interaction_type:
            "authentication-device-notification-no-action",
        },
      },
    });
    expect(cibaClientResp.status).toBe(201);

    // Register authentication configuration
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtToken}` },
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
                properties: {
                  username: { type: "string" },
                  password: { type: "string" },
                },
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
  });

  async function executeCibaFlow(scope) {
    // Step 1: Backchannel authentication request
    const cibaResp = await requestBackchannelAuthentications({
      endpoint: `${backendUrl}/${tenantId}/v1/backchannel/authentications`,
      clientId: cibaClientId,
      clientSecret: cibaClientSecret,
      scope,
      loginHint: `device:${deviceId},idp:idp-server`,
    });
    expect(cibaResp.status).toBe(200);
    expect(cibaResp.data.auth_req_id).toBeDefined();

    // Step 2: Get and complete authentication transaction
    const txResp =
      await getAuthenticationDeviceAuthenticationTransaction({
        endpoint: `${backendUrl}/${tenantId}/v1/authentication-devices/{id}/authentications`,
        deviceId,
        params: { "attributes.auth_req_id": cibaResp.data.auth_req_id },
      });
    expect(txResp.status).toBe(200);
    expect(txResp.data.list.length).toBeGreaterThan(0);

    const tx = txResp.data.list[0];
    await postAuthenticationDeviceInteraction({
      endpoint: `${backendUrl}/${tenantId}/v1/authentications/{id}/`,
      flowType: tx.flow,
      id: tx.id,
      interactionType: "password-authentication",
      body: { username: userEmail, password: userPassword },
    });

    // Step 3: Token request
    const tokenResp = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "urn:openid:params:grant-type:ciba",
      authReqId: cibaResp.data.auth_req_id,
      clientId: cibaClientId,
      clientSecret: cibaClientSecret,
    });
    expect(tokenResp.status).toBe(200);
    expect(tokenResp.data.id_token).toBeDefined();

    return tokenResp;
  }

  it("should include ex_sub and employee_number custom claims in CIBA ID token", async () => {
    console.log("\n=== CIBA Custom Claims Test ===");

    const tokenResp = await executeCibaFlow(
      "openid claims:ex_sub claims:status claims:employee_number"
    );

    const jwksResponse = await getJwks({
      endpoint: `${backendUrl}/${tenantId}/v1/jwks`,
    });
    const decoded = verifyAndDecodeJwt({
      jwt: tokenResp.data.id_token,
      jwks: jwksResponse.data,
    });

    expect(decoded.verifyResult).toBe(true);

    // Header
    expect(decoded.header.alg).toBeDefined();
    expect(decoded.header.kid).toBeDefined();

    // Required standard claims
    expect(decoded.payload.iss).toBe(`${backendUrl}/${tenantId}`);
    expect(decoded.payload.sub).toBe(userSub);
    expect(decoded.payload.aud).toContain(cibaClientId);
    expect(isNumber(decoded.payload.exp)).toBe(true);
    expect(isNumber(decoded.payload.iat)).toBe(true);
    expect(decoded.payload.exp).toBeGreaterThan(decoded.payload.iat);

    // CIBA always involves authentication, so auth_time and amr are expected
    expect(isNumber(decoded.payload.auth_time)).toBe(true);
    expect(isArray(decoded.payload.amr)).toBe(true);
    expect(decoded.payload.amr).toContain("password");

    // Custom claims
    expect(decoded.payload.ex_sub).toBe(externalUserId);
    expect(decoded.payload.status).toBe("REGISTERED");
    expect(decoded.payload.employee_number).toBe(employeeNumber);

    console.log("Standard + custom claims verified");
  });

  it("should include RBAC claims in CIBA ID token", async () => {
    console.log("\n=== CIBA RBAC Claims Test ===");

    const tokenResp = await executeCibaFlow(
      "openid claims:roles claims:permissions"
    );

    const jwksResponse = await getJwks({
      endpoint: `${backendUrl}/${tenantId}/v1/jwks`,
    });
    const decoded = verifyAndDecodeJwt({
      jwt: tokenResp.data.id_token,
      jwks: jwksResponse.data,
    });

    expect(decoded.verifyResult).toBe(true);

    // Required standard claims
    expect(decoded.payload.iss).toBe(`${backendUrl}/${tenantId}`);
    expect(decoded.payload.sub).toBe(userSub);
    expect(decoded.payload.aud).toContain(cibaClientId);
    expect(isNumber(decoded.payload.exp)).toBe(true);
    expect(isNumber(decoded.payload.iat)).toBe(true);

    // RBAC claims
    expect(decoded.payload.roles).toBeDefined();
    expect(isArray(decoded.payload.roles)).toBe(true);
    expect(decoded.payload.roles.length).toBeGreaterThan(0);

    expect(decoded.payload.permissions).toBeDefined();
    expect(isArray(decoded.payload.permissions)).toBe(true);
    expect(decoded.payload.permissions.length).toBeGreaterThan(0);

    console.log(`Roles: ${decoded.payload.roles}`);
    console.log(`Permissions count: ${decoded.payload.permissions.length}`);
  });

  it("should not include custom claims when claims: scope is not requested", async () => {
    console.log("\n=== CIBA No Custom Claims Test ===");

    const tokenResp = await executeCibaFlow("openid");

    const jwksResponse = await getJwks({
      endpoint: `${backendUrl}/${tenantId}/v1/jwks`,
    });
    const decoded = verifyAndDecodeJwt({
      jwt: tokenResp.data.id_token,
      jwks: jwksResponse.data,
    });

    expect(decoded.verifyResult).toBe(true);

    // Required standard claims present
    expect(decoded.payload.iss).toBe(`${backendUrl}/${tenantId}`);
    expect(decoded.payload.sub).toBe(userSub);
    expect(decoded.payload.aud).toContain(cibaClientId);
    expect(isNumber(decoded.payload.exp)).toBe(true);
    expect(isNumber(decoded.payload.iat)).toBe(true);

    // Custom claims absent
    expect(decoded.payload.ex_sub).toBeUndefined();
    expect(decoded.payload.status).toBeUndefined();
    expect(decoded.payload.roles).toBeUndefined();
    expect(decoded.payload.employee_number).toBeUndefined();

    console.log("Verified: no custom claims without claims: scope");
  });
});
