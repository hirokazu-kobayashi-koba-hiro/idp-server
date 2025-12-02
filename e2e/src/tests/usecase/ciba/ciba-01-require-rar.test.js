import { describe, expect, it, beforeAll } from "@jest/globals";
import { deletion, postWithJson } from "../../../lib/http";
import {
  getAuthenticationDeviceAuthenticationTransaction,
  postAuthenticationDeviceInteraction,
  requestBackchannelAuthentications,
  requestToken
} from "../../../api/oauthClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";

/**
 * CIBA Use Case: RAR Required Configuration
 *
 * This test demonstrates client-level RAR requirement enforcement:
 * 1. Create organization and tenant via Onboarding API
 * 2. Create CIBA client with ciba_require_rar=true
 * 3. Verify CIBA request without authorization_details fails
 * 4. Verify CIBA request with authorization_details succeeds
 * 5. Clean up resources
 */
describe("CIBA Use Case: Client-Level RAR Requirement", () => {
  let systemAccessToken;

  beforeAll(async () => {
    const tokenResponse = await requestToken({
      endpoint: adminServerConfig.tokenEndpoint,
      grantType: "password",
      username: adminServerConfig.oauth.username,
      password: adminServerConfig.oauth.password,
      scope: adminServerConfig.adminClient.scope,
      clientId: adminServerConfig.adminClient.clientId,
      clientSecret: adminServerConfig.adminClient.clientSecret,
    });
    expect(tokenResponse.status).toBe(200);
    systemAccessToken = tokenResponse.data.access_token;
  });

  it("should enforce RAR requirement when ciba_require_rar is enabled", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const userId = uuidv4();
    const orgClientId = uuidv4();
    const orgAdminEmail = `admin-${timestamp}@test-org.example.com`;
    const orgAdminPassword = `TestOrgPass${timestamp}!`;
    const orgClientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const cibaClientId = uuidv4();
    const cibaClientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();
    const authenticationDeviceId = uuidv4();

    console.log("\n=== Step 1: Create Organization and Tenant ===");

    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `CIBA RAR Test Org ${timestamp}`,
        description: "CIBA RAR requirement test organization",
      },
      tenant: {
        id: tenantId,
        name: `CIBA RAR Test Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `CIBA_RAR_SESSION_${organizationId.substring(0, 8)}`,
          use_secure_cookie: false,
        },
        cors_config: {
          allow_origins: [backendUrl],
        },
        security_event_log_config: {
          format: "structured_json",
          stage: "processed",
          include_user_id: true,
          include_client_id: true,
          persistence_enabled: true,
        },
      },
      authorization_server: {
        issuer: `${backendUrl}/${tenantId}`,
        authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
        token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        token_endpoint_auth_methods_supported: ["client_secret_post", "client_secret_basic"],
        userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
        jwks: jwksContent,
        grant_types_supported: ["authorization_code", "refresh_token", "password", "urn:openid:params:grant-type:ciba"],
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        scopes_supported: ["openid", "profile", "email", "management"],
        response_types_supported: ["code"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["RS256", "ES256"],
        token_introspection_endpoint: `${backendUrl}/${tenantId}/v1/tokens/introspection`,
        token_revocation_endpoint: `${backendUrl}/${tenantId}/v1/tokens/revocation`,
        backchannel_authentication_endpoint: `${backendUrl}/${tenantId}/v1/backchannel/authentications`,
        backchannel_token_delivery_modes_supported: ["poll", "ping"],
        backchannel_authentication_request_signing_alg_values_supported: ["RS256", "ES256"],
        backchannel_user_code_parameter_supported: true,
        authorization_details_types_supported: ["account_information", "payment_initiation"],
        response_modes_supported: [
          "query"
        ],
        extension: {
          access_token_type: "JWT",
          backchannel_authentication_polling_interval: 5,
          backchannel_authentication_request_expires_in: 300,
          required_backchannel_auth_user_code: true,
          backchannel_auth_user_code_type: "numeric",
        },
      },
      user: {
        sub: userId,
        email: orgAdminEmail,
        raw_password: orgAdminPassword,
        username: orgAdminEmail,
        authentication_devices: [
          {
            id: authenticationDeviceId,
            app_name: "test app"
          }
        ]
      },
      client: {
        client_id: orgClientId,
        client_id_alias: `test-org-client-${timestamp}`,
        client_secret: orgClientSecret,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: ["authorization_code", "refresh_token", "password"],
        scope: "openid profile email management",
        client_name: `Test Organization Client ${timestamp}`,
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
      },
    };

    const onboardingResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      body: onboardingRequest,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });

    console.log("Onboarding response:", JSON.stringify(onboardingResponse.data, null, 2));
    expect(onboardingResponse.status).toBe(201);

    console.log("\n=== Step 2: Login as Organization Admin ===");

    const adminTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: orgAdminEmail,
      password: orgAdminPassword,
      scope: "openid profile email management",
      clientId: orgClientId,
      clientSecret: orgClientSecret,
    });

    expect(adminTokenResponse.status).toBe(200);
    const orgAdminAccessToken = adminTokenResponse.data.access_token;

    console.log("\n=== Step 3: Create CIBA Client with ciba_require_rar=true ===");

    const clientRequest = {
      client_id: cibaClientId,
      client_secret: cibaClientSecret,
      client_name: `CIBA RAR Required Client ${timestamp}`,
      redirect_uris: [`${backendUrl}/callback`],
      token_endpoint_auth_method: "client_secret_post",
      grant_types: ["urn:openid:params:grant-type:ciba"],
      scope: "openid profile email",
      backchannel_token_delivery_mode: "poll",
      backchannel_user_code_parameter: true,
      authorization_details_types: ["account_information", "payment_initiation"],
      extension: {
        ciba_require_rar: true,
        default_ciba_authentication_interaction_type: "authentication-device-notification-no-action",
      },
    };

    const clientCreationResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients`,
      body: clientRequest,
      headers: { Authorization: `Bearer ${orgAdminAccessToken}` },
    });

    console.log("Client creation response:", JSON.stringify(clientCreationResponse.data, null, 2));
    expect(clientCreationResponse.status).toBe(201);
    expect(clientCreationResponse.data.result.extension.ciba_require_rar).toBe(true);

    console.log("\n=== Step 4: Verify CIBA Request WITHOUT authorization_details Fails (400 Bad Request) ===");

    const cibaRequestWithoutRar = await requestBackchannelAuthentications({
      endpoint: `${backendUrl}/${tenantId}/v1/backchannel/authentications`,
      clientId: cibaClientId,
      scope: "openid profile email",
      bindingMessage: "Test",
      userCode: "123456",
      loginHint: `device:${authenticationDeviceId},idp:idp-server`,
      clientSecret: cibaClientSecret,
    });

    console.log("CIBA request without RAR response:", JSON.stringify(cibaRequestWithoutRar.data, null, 2));
    expect(cibaRequestWithoutRar.status).toBe(400);
    expect(cibaRequestWithoutRar.data.error).toBe("invalid_request");
    expect(cibaRequestWithoutRar.data.error_description).toContain("authorization_details is required");

    console.log("\n=== Step 5: Verify CIBA Request WITH authorization_details Succeeds ===");

    const authorizationDetails = [{
      "type": "account_information",
      "actions": ["list_accounts", "read_balances"],
      "locations": ["https://example.com/accounts"]
    }];

    const cibaRequestWithRar = await requestBackchannelAuthentications({
      endpoint: `${backendUrl}/${tenantId}/v1/backchannel/authentications`,
      clientId: cibaClientId,
      scope: "openid profile email",
      bindingMessage: "Test",
      userCode: "123456",
      authorizationDetails: JSON.stringify(authorizationDetails),
      loginHint: `device:${authenticationDeviceId},idp:idp-server`,
      clientSecret: cibaClientSecret,
    });

    console.log("CIBA request with RAR response:", JSON.stringify(cibaRequestWithRar.data, null, 2));
    expect(cibaRequestWithRar.status).toBe(200);
    expect(cibaRequestWithRar.data).toHaveProperty("auth_req_id");
    expect(cibaRequestWithRar.data).toHaveProperty("expires_in");

    // Complete CIBA authentication
    const authReqId = cibaRequestWithRar.data.auth_req_id;

    console.log("\n=== Step 6: Complete CIBA Authentication ===");

    const authenticationTransactionResponse = await getAuthenticationDeviceAuthenticationTransaction({
      endpoint: `${backendUrl}/${tenantId}/v1/authentication-devices/{id}/authentications`,
      deviceId: authenticationDeviceId,
      params: {
        "attributes.auth_req_id": authReqId
      },
    });

    console.log(JSON.stringify(authenticationTransactionResponse.data, null, 2));
    expect(authenticationTransactionResponse.status).toBe(200);
    const authenticationTransaction = authenticationTransactionResponse.data.list[0];

    const completeResponse = await postAuthenticationDeviceInteraction({
      endpoint: `${backendUrl}/${tenantId}/v1/authentications/{id}/`,
      flowType: authenticationTransaction.flow,
      id: authenticationTransaction.id,
      interactionType: "password-authentication",
      body: {
        username: orgAdminEmail,
        password: orgAdminPassword,
      }
    });
    expect(completeResponse.status).toBe(200);

    console.log("\n=== Step 7: Request Token with authorization_details ===");

    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "urn:openid:params:grant-type:ciba",
      authReqId: authReqId,
      clientId: cibaClientId,
      clientSecret: cibaClientSecret,
    });

    console.log("Token response:", JSON.stringify(tokenResponse.data, null, 2));
    expect(tokenResponse.status).toBe(200);
    expect(tokenResponse.data).toHaveProperty("authorization_details");
    expect(Array.isArray(tokenResponse.data.authorization_details)).toBe(true);
    expect(tokenResponse.data.authorization_details).toHaveLength(1);
    expect(tokenResponse.data.authorization_details[0].type).toBe("account_information");

    console.log("\n=== Step 8: Clean Up Resources ===");

    // Delete client
    const clientDeletionResponse = await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients/${orgClientId}`,
      headers: { Authorization: `Bearer ${orgAdminAccessToken}` },
    });
    expect(clientDeletionResponse.status).toBe(204);

    // Delete tenant
    const tenantDeletionResponse = await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`,
      headers: { Authorization: `Bearer ${orgAdminAccessToken}` },
    });
    expect(tenantDeletionResponse.status).toBe(204);

    // Delete organization
    const orgDeletionResponse = await deletion({
      url: `${backendUrl}/v1/management/orgs/${organizationId}`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });
    expect(orgDeletionResponse.status).toBe(204);

    console.log("\n=== Test Completed Successfully ===");
  });

  it("should allow CIBA request without authorization_details when ciba_require_rar is false", async () => {
    const timestamp = Date.now();
    const orgId = uuidv4();
    const tenantId = uuidv4();
    const userId = uuidv4();
    const orgClientId = uuidv4();
    const orgAdminEmail = `admin-${timestamp}@test-org2.example.com`;
    const orgAdminPassword = `TestOrgPass${timestamp}!`;
    const orgClientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();
    const cibaClientId = uuidv4();
    const cibaClientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const authenticationDeviceId = uuidv4();

    console.log("\n=== Step 1: Create Organization and Tenant (ciba_require_rar=false) ===");

    const onboardingRequest = {
      organization: {
        id: orgId,
        name: `CIBA No RAR Req Test Org ${timestamp}`,
        description: "CIBA no RAR requirement test organization",
      },
      tenant: {
        id: tenantId,
        name: `CIBA No RAR Req Test Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `CIBA_NORAR_SESSION_${orgId.substring(0, 8)}`,
          use_secure_cookie: false,
        },
        cors_config: {
          allow_origins: [backendUrl],
        },
      },
      authorization_server: {
        issuer: `${backendUrl}/${tenantId}`,
        authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
        token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        token_endpoint_auth_methods_supported: ["client_secret_post"],
        userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
        jwks: jwksContent,
        grant_types_supported: ["urn:openid:params:grant-type:ciba", "password"],
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        scopes_supported: ["openid", "profile", "email"],
        response_types_supported: ["code"],
        subject_types_supported: ["public"],
        backchannel_authentication_endpoint: `${backendUrl}/${tenantId}/v1/backchannel/authentications`,
        backchannel_token_delivery_modes_supported: ["poll"],
        backchannel_user_code_parameter_supported: true,
        authorization_details_types_supported: ["account_information"],
        response_modes_supported: ["query"],
        extension: {
          access_token_type: "JWT",
          backchannel_authentication_polling_interval: 5,
          backchannel_authentication_request_expires_in: 300,
          required_backchannel_auth_user_code: true,
          backchannel_auth_user_code_type: "numeric",
        },
      },
      user: {
        sub: userId,
        email: orgAdminEmail,
        raw_password: orgAdminPassword,
        username: orgAdminEmail,
        authentication_devices: [
          {
            id: authenticationDeviceId,
            app_name: "test app"
          }
        ]
      },
      client: {
        client_id: orgClientId,
        client_id_alias: `test-org-client-${timestamp}`,
        client_secret: orgClientSecret,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: ["authorization_code", "refresh_token", "password"],
        scope: "openid profile email management",
        client_name: `Test Organization Client ${timestamp}`,
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
      },
    };

    const onboardingResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      body: onboardingRequest,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });

    console.log(JSON.stringify(onboardingResponse.data, null, 2));
    expect(onboardingResponse.status).toBe(201);

    console.log("\n=== Step 2: Login as Organization Admin ===");

    const adminTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: orgAdminEmail,
      password: orgAdminPassword,
      scope: "openid profile email management",
      clientId: orgClientId,
      clientSecret: orgClientSecret,
    });

    console.log(JSON.stringify(adminTokenResponse.data, null, 2));
    expect(adminTokenResponse.status).toBe(200);
    const orgAdminAccessToken = adminTokenResponse.data.access_token;

    console.log("\n=== Step 2: Create CIBA Client with ciba_require_rar=false (default) ===");

    const clientRequest = {
      client_id: cibaClientId,
      client_secret: cibaClientSecret,
      client_name: `CIBA RAR Optional Client ${timestamp}`,
      redirect_uris: [`${backendUrl}/callback`],
      token_endpoint_auth_method: "client_secret_post",
      grant_types: ["urn:openid:params:grant-type:ciba"],
      scope: "openid profile email",
      backchannel_token_delivery_mode: "poll",
      backchannel_user_code_parameter: true,
      authorization_details_types: ["account_information"],
      extension: {
        ciba_require_rar: false,
        default_ciba_authentication_interaction_type: "authentication-device-notification-no-action",
      },
    };

    const clientCreationResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/clients`,
      body: clientRequest,
      headers: { Authorization: `Bearer ${orgAdminAccessToken}` },
    });

    expect(clientCreationResponse.status).toBe(201);
    expect(clientCreationResponse.data.result.extension.ciba_require_rar).toBe(false);

    console.log("\n=== Step 3: Verify CIBA Request WITHOUT authorization_details Succeeds ===");

    const cibaRequestWithoutRar = await requestBackchannelAuthentications({
      endpoint: `${backendUrl}/${tenantId}/v1/backchannel/authentications`,
      clientId: cibaClientId,
      scope: "openid profile email",
      bindingMessage: "Test",
      userCode: "123456",
      loginHint: `device:${authenticationDeviceId},idp:idp-server`,
      clientSecret: cibaClientSecret,
    });

    console.log("CIBA request without RAR response:", JSON.stringify(cibaRequestWithoutRar.data, null, 2));
    expect(cibaRequestWithoutRar.status).toBe(200);
    expect(cibaRequestWithoutRar.data).toHaveProperty("auth_req_id");

    console.log("\n=== Step 4: Clean Up Resources ===");

    // Delete client
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/clients/${orgClientId}`,
      headers: { Authorization: `Bearer ${orgAdminAccessToken}` },
    });

    // Delete tenant
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}`,
      headers: { Authorization: `Bearer ${orgAdminAccessToken}` },
    });

    // Delete organization
    await deletion({
      url: `${backendUrl}/v1/management/orgs/${orgId}`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });

    console.log("\n=== Test Completed Successfully ===");
  });
});
