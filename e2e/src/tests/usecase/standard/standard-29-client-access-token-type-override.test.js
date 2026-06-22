import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { deletion, get, postWithJson } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { onboarding } from "../../../api/managementClient";
import { generateECP256JWKS, verifyAndDecodeJwt } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";

/**
 * Standard Use Case: Client-Level access_token_type Override (Issue #1433)
 *
 * Verifies that the access token format (opaque / JWT) can be overridden per client via
 * ClientExtensionConfiguration, mirroring the access_token_duration / id_token_duration override
 * pattern. When a client does not configure access_token_type, it falls back to the
 * authorization-server default.
 *
 * Test setup:
 * - Tenant: access_token_type = JWT (server default for this tenant)
 * - Client A: no override -> inherits JWT
 * - Client B: extension.access_token_type = opaque -> opaque random string
 *
 * Key behaviour verified:
 * - Client A (fallback): access token is a 3-part JWT, verifiable via JWKS
 * - Client B (override): access token is an opaque string, NOT a JWT
 */
describe("Standard Use Case: Client-Level access_token_type Override (#1433)", () => {
  let systemAccessToken;
  let organizationId;
  let tenantId;
  let adminEmail;
  let adminPassword;
  let clientAId;
  let clientASecret;
  let clientBId;
  let clientBSecret;
  let jwksContent;

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

    const timestamp = Date.now();
    organizationId = uuidv4();
    tenantId = uuidv4();
    const adminUserId = uuidv4();
    clientAId = uuidv4();
    clientASecret = `secret-a-${crypto.randomBytes(16).toString("hex")}`;
    clientBId = uuidv4();
    clientBSecret = `secret-b-${crypto.randomBytes(16).toString("hex")}`;
    adminEmail = `admin-${timestamp}@at-type-override-test.example.com`;
    adminPassword = `AdminPass${timestamp}!`;
    jwksContent = await generateECP256JWKS();

    // Create organization with tenant (server default access_token_type = JWT)
    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `AT Type Override Test Org ${timestamp}`,
        description: "E2E test for client-level access_token_type override (#1433)",
      },
      tenant: {
        id: tenantId,
        name: `AT Type Override Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `AT_TYPE_${organizationId.substring(0, 8)}`,
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
        token_endpoint_auth_methods_supported: [
          "client_secret_post",
          "client_secret_basic",
        ],
        userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
        jwks: jwksContent,
        grant_types_supported: [
          "authorization_code",
          "refresh_token",
          "password",
        ],
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        scopes_supported: [
          "openid",
          "profile",
          "email",
          "org-management",
          "management",
        ],
        claims_supported: ["sub", "iss", "auth_time", "acr", "name", "email", "email_verified"],
        response_types_supported: ["code"],
        response_modes_supported: ["query"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["ES256"],
        extension: {
          access_token_type: "JWT",
        },
      },
      user: {
        sub: adminUserId,
        provider_id: "idp-server",
        name: "AT Type Override Test Admin",
        email: adminEmail,
        email_verified: true,
        raw_password: adminPassword,
      },
      client: {
        client_id: clientAId,
        client_secret: clientASecret,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: ["authorization_code", "refresh_token", "password"],
        scope: "openid profile email org-management management",
        client_name: "Client A - Tenant Default (JWT)",
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
      },
    };

    const createResponse = await onboarding({
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: onboardingRequest,
    });
    if (createResponse.status !== 201) {
      console.error(
        "Onboarding failed:",
        JSON.stringify(createResponse.data, null, 2)
      );
    }
    expect(createResponse.status).toBe(201);

    // Get management token
    const mgmtTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: adminEmail,
      password: adminPassword,
      scope: "org-management management",
      clientId: clientAId,
      clientSecret: clientASecret,
    });
    expect(mgmtTokenResponse.status).toBe(200);
    const mgmtAccessToken = mgmtTokenResponse.data.access_token;

    // Register Client B with access_token_type = opaque override
    const clientBRequest = {
      client_id: clientBId,
      client_secret: clientBSecret,
      redirect_uris: ["http://localhost:3000/callback"],
      response_types: ["code"],
      grant_types: ["authorization_code", "refresh_token", "password"],
      scope: "openid profile email",
      client_name: "Client B - Override opaque",
      token_endpoint_auth_method: "client_secret_post",
      application_type: "web",
      extension: {
        access_token_type: "opaque",
      },
    };

    const clientBResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: clientBRequest,
    });
    if (clientBResponse.status !== 201) {
      console.error(
        "Client B creation failed:",
        JSON.stringify(clientBResponse.data, null, 2)
      );
    }
    expect(clientBResponse.status).toBe(201);
  });

  afterAll(async () => {
    const cleanupTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: adminEmail,
      password: adminPassword,
      scope: "org-management management",
      clientId: clientAId,
      clientSecret: clientASecret,
    });
    const cleanupToken = cleanupTokenResponse.data?.access_token;

    if (cleanupToken) {
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`,
        headers: { Authorization: `Bearer ${cleanupToken}` },
      }).catch(() => {});
    }

    await deletion({
      url: `${backendUrl}/v1/management/orgs/${organizationId}`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    }).catch(() => {});
  });

  it("Client A (no override): access token inherits tenant JWT format and verifies via JWKS", async () => {
    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: adminEmail,
      password: adminPassword,
      scope: "openid profile email",
      clientId: clientAId,
      clientSecret: clientASecret,
    });
    expect(tokenResponse.status).toBe(200);

    const accessToken = tokenResponse.data.access_token;
    // JWT has 3 parts separated by dots
    expect(accessToken.split(".").length).toBe(3);

    const jwksResponse = await get({ url: `${backendUrl}/${tenantId}/v1/jwks` });
    expect(jwksResponse.status).toBe(200);

    const { payload } = verifyAndDecodeJwt({
      jwt: accessToken,
      jwks: jwksResponse.data,
    });
    expect(payload.iss).toBe(`${backendUrl}/${tenantId}`);
    expect(payload.sub).toBeDefined();
  });

  it("Client B (override opaque): access token is an opaque string, not a JWT", async () => {
    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: adminEmail,
      password: adminPassword,
      scope: "openid profile email",
      clientId: clientBId,
      clientSecret: clientBSecret,
    });
    expect(tokenResponse.status).toBe(200);

    const accessToken = tokenResponse.data.access_token;
    // opaque token is a random identifier, NOT a 3-part JWT
    expect(accessToken.split(".").length).not.toBe(3);
  });
});
