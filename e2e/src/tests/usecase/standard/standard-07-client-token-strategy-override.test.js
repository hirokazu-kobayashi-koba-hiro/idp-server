import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { deletion, get, postWithJson, putWithJson } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { generateECP256JWKS, verifyAndDecodeJwt } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import { sleep } from "../../../lib/util";

/**
 * Standard Use Case: Client-Level Token Strategy Override
 *
 * This test verifies that token expiration strategy settings
 * (refresh_token_strategy, rotate_refresh_token, id_token_duration)
 * can be overridden at the client level via ClientExtensionConfiguration.
 *
 * Test setup:
 * - Tenant: FIXED strategy + rotate=true (default secure pattern)
 * - Client A: No override (inherits tenant settings)
 * - Client B: EXTENDS strategy + rotate=false + id_token_duration=60
 *
 * Key behaviour verified:
 * - Client A (tenant default): refresh rotates token value, expiration stays fixed
 * - Client B (override): refresh keeps same token value, expiration extends
 * - Client B: ID token has shorter expiration than tenant default
 */
describe("Standard Use Case: Client-Level Token Strategy Override", () => {
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
    adminEmail = `admin-${timestamp}@token-strategy-test.example.com`;
    adminPassword = `AdminPass${timestamp}!`;
    jwksContent = await generateECP256JWKS();

    // Create organization with tenant (FIXED + rotate=true, id_token_duration=3600)
    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `Token Strategy Override Test Org ${timestamp}`,
        description: "E2E test for client-level token strategy override",
      },
      tenant: {
        id: tenantId,
        name: `Token Strategy Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `TS_SESSION_${organizationId.substring(0, 8)}`,
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
        response_types_supported: ["code"],
        response_modes_supported: ["query"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["ES256"],
        extension: {
          access_token_type: "JWT",
          access_token_duration: 3600,
          id_token_duration: 3600,
          refresh_token_duration: 86400,
          refresh_token_strategy: "FIXED",
          rotate_refresh_token: true,
        },
      },
      user: {
        sub: adminUserId,
        provider_id: "idp-server",
        name: "Token Strategy Test Admin",
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
        client_name: "Client A - Tenant Default",
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
      },
    };

    const createResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
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

    // Register Client B with EXTENDS + !rotate + id_token_duration=60
    const clientBRequest = {
      client_id: clientBId,
      client_secret: clientBSecret,
      redirect_uris: ["http://localhost:3000/callback"],
      response_types: ["code"],
      grant_types: ["authorization_code", "refresh_token", "password"],
      scope: "openid profile email",
      client_name: "Client B - Override EXTENDS + No Rotate",
      token_endpoint_auth_method: "client_secret_post",
      application_type: "web",
      extension: {
        refresh_token_strategy: "EXTENDS",
        rotate_refresh_token: false,
        id_token_duration: 60,
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
    // Cleanup: get fresh token and delete resources
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

  it("Client A (tenant default FIXED+rotate): refresh rotates token value, expiration stays fixed", async () => {
    // Get initial tokens
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
    expect(tokenResponse.data.refresh_token).toBeDefined();

    const initialRefreshToken = tokenResponse.data.refresh_token;

    await sleep(1000);

    // Refresh
    const refreshResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "refresh_token",
      refreshToken: initialRefreshToken,
      clientId: clientAId,
      clientSecret: clientASecret,
    });

    expect(refreshResponse.status).toBe(200);
    expect(refreshResponse.data.refresh_token).toBeDefined();

    // FIXED + rotate: token value must change
    expect(refreshResponse.data.refresh_token).not.toBe(initialRefreshToken);
  });

  it("Client B (override EXTENDS+!rotate): refresh keeps same token value", async () => {
    // Get initial tokens
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
    expect(tokenResponse.data.refresh_token).toBeDefined();

    const initialRefreshToken = tokenResponse.data.refresh_token;

    await sleep(1000);

    // Refresh
    const refreshResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "refresh_token",
      refreshToken: initialRefreshToken,
      clientId: clientBId,
      clientSecret: clientBSecret,
    });

    expect(refreshResponse.status).toBe(200);
    expect(refreshResponse.data.refresh_token).toBeDefined();

    // EXTENDS + !rotate: token value must remain the same
    expect(refreshResponse.data.refresh_token).toBe(initialRefreshToken);
  });

  it("Client B (override id_token_duration=60): ID token has shorter expiration than tenant default", async () => {
    // Get tokens for Client B
    const tokenBResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: adminEmail,
      password: adminPassword,
      scope: "openid profile email",
      clientId: clientBId,
      clientSecret: clientBSecret,
    });
    expect(tokenBResponse.status).toBe(200);
    expect(tokenBResponse.data.id_token).toBeDefined();

    // Get tokens for Client A (tenant default)
    const tokenAResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: adminEmail,
      password: adminPassword,
      scope: "openid profile email",
      clientId: clientAId,
      clientSecret: clientASecret,
    });
    expect(tokenAResponse.status).toBe(200);
    expect(tokenAResponse.data.id_token).toBeDefined();

    // Decode and get JWKS for verification
    const jwksResponse = await get({
      url: `${backendUrl}/${tenantId}/v1/jwks`,
    });
    expect(jwksResponse.status).toBe(200);
    const jwks = jwksResponse.data;

    const idTokenA = verifyAndDecodeJwt({
      jwt: tokenAResponse.data.id_token,
      jwks,
    });
    const idTokenB = verifyAndDecodeJwt({
      jwt: tokenBResponse.data.id_token,
      jwks,
    });

    // Client A: id_token_duration = 3600 (tenant default)
    const durationA = idTokenA.payload.exp - idTokenA.payload.iat;
    // Client B: id_token_duration = 60 (client override)
    const durationB = idTokenB.payload.exp - idTokenB.payload.iat;

    console.log(`Client A ID token duration: ${durationA}s (expected ~3600)`);
    console.log(`Client B ID token duration: ${durationB}s (expected ~60)`);

    // Client A should have tenant default duration (~3600s)
    expect(durationA).toBeGreaterThanOrEqual(3500);
    expect(durationA).toBeLessThanOrEqual(3700);

    // Client B should have overridden duration (~60s)
    expect(durationB).toBeGreaterThanOrEqual(50);
    expect(durationB).toBeLessThanOrEqual(70);
  });
});
