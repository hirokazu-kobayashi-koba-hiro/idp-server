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
 * Coverage is symmetric across both server defaults so neither override direction is assumed:
 * - Tenant default JWT:    Client A (no override) -> JWT (fallback),  Client B (opaque) -> opaque
 * - Tenant default opaque: Client A (no override) -> opaque (fallback), Client B (JWT) -> JWT
 */
describe("Standard Use Case: Client-Level access_token_type Override (#1433)", () => {
  let systemAccessToken;
  let jwtTenant; // server default = JWT
  let opaqueTenant; // server default = opaque

  /** Onboards an org + tenant (with the given server-level access_token_type) + admin + base client. */
  const onboardTenant = async (serverAccessTokenType, suffix) => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const adminUserId = uuidv4();
    const clientAId = uuidv4();
    const clientASecret = `secret-a-${crypto.randomBytes(16).toString("hex")}`;
    const adminEmail = `admin-${timestamp}-${suffix}@at-type-override-test.example.com`;
    const adminPassword = `AdminPass${timestamp}!`;
    const jwksContent = await generateECP256JWKS();

    const onboardingResponse = await onboarding({
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: {
        organization: {
          id: organizationId,
          name: `AT Type Override Org ${suffix} ${timestamp}`,
          description: "E2E test for client-level access_token_type override (#1433)",
        },
        tenant: {
          id: tenantId,
          name: `AT Type Override Tenant ${suffix} ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          session_config: {
            cookie_name: `AT_${suffix}_${organizationId.substring(0, 8)}`,
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
            access_token_type: serverAccessTokenType,
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
          client_name: `Client A (${suffix}) - no override`,
          token_endpoint_auth_method: "client_secret_post",
          application_type: "web",
        },
      },
    });
    if (onboardingResponse.status !== 201) {
      console.error("Onboarding failed:", JSON.stringify(onboardingResponse.data, null, 2));
    }
    expect(onboardingResponse.status).toBe(201);

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

    const ctx = {
      organizationId,
      tenantId,
      adminEmail,
      adminPassword,
      clientAId,
      clientASecret,
      mgmtAccessToken: mgmtTokenResponse.data.access_token,
    };

    return ctx;
  };

  /** Registers a client overriding access_token_type and returns its credentials. */
  const registerOverrideClient = async (ctx, accessTokenTypeOverride, name) => {
    const clientId = uuidv4();
    const clientSecret = `secret-${crypto.randomBytes(16).toString("hex")}`;
    const response = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${ctx.organizationId}/tenants/${ctx.tenantId}/clients`,
      headers: { Authorization: `Bearer ${ctx.mgmtAccessToken}` },
      body: {
        client_id: clientId,
        client_secret: clientSecret,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: ["authorization_code", "refresh_token", "password"],
        scope: "openid profile email",
        client_name: name,
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
        extension: {
          access_token_type: accessTokenTypeOverride,
        },
      },
    });
    if (response.status !== 201) {
      console.error("Client creation failed:", JSON.stringify(response.data, null, 2));
    }
    expect(response.status).toBe(201);
    return { clientId, clientSecret };
  };

  /** Issues an access token via the password grant for the given client. */
  const issueAccessToken = async (ctx, clientId, clientSecret) => {
    const response = await requestToken({
      endpoint: `${backendUrl}/${ctx.tenantId}/v1/tokens`,
      grantType: "password",
      username: ctx.adminEmail,
      password: ctx.adminPassword,
      scope: "openid profile email",
      clientId,
      clientSecret,
    });
    expect(response.status).toBe(200);
    return response.data.access_token;
  };

  const expectJwt = async (ctx, accessToken) => {
    // JWT has 3 parts separated by dots and verifies against the tenant JWKS
    expect(accessToken.split(".").length).toBe(3);
    const jwksResponse = await get({ url: `${backendUrl}/${ctx.tenantId}/v1/jwks` });
    expect(jwksResponse.status).toBe(200);
    const { payload } = verifyAndDecodeJwt({ jwt: accessToken, jwks: jwksResponse.data });
    expect(payload.iss).toBe(`${backendUrl}/${ctx.tenantId}`);
    expect(payload.sub).toBeDefined();
  };

  const expectOpaque = (accessToken) => {
    // opaque token is a random identifier (RandomStringGenerator(32), alphanumeric) -> no dots
    expect(accessToken.split(".").length).toBe(1);
  };

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

    // Tenant whose server default is JWT; register a client overriding to opaque
    jwtTenant = await onboardTenant("JWT", "jwt");
    jwtTenant.opaqueClient = await registerOverrideClient(
      jwtTenant,
      "opaque",
      "Client B (jwt-tenant) - override opaque"
    );

    // Tenant whose server default is opaque; register a client overriding to JWT
    opaqueTenant = await onboardTenant("opaque", "opaque");
    opaqueTenant.jwtClient = await registerOverrideClient(
      opaqueTenant,
      "JWT",
      "Client B (opaque-tenant) - override JWT"
    );
  });

  afterAll(async () => {
    for (const ctx of [jwtTenant, opaqueTenant]) {
      if (!ctx) continue;
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${ctx.organizationId}/tenants/${ctx.tenantId}`,
        headers: { Authorization: `Bearer ${ctx.mgmtAccessToken}` },
      }).catch(() => {});
      await deletion({
        url: `${backendUrl}/v1/management/orgs/${ctx.organizationId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }
  });

  it("tenant default JWT, client without override: access token falls back to JWT", async () => {
    const accessToken = await issueAccessToken(
      jwtTenant,
      jwtTenant.clientAId,
      jwtTenant.clientASecret
    );
    await expectJwt(jwtTenant, accessToken);
  });

  it("tenant default JWT, client overrides to opaque: access token is opaque", async () => {
    const accessToken = await issueAccessToken(
      jwtTenant,
      jwtTenant.opaqueClient.clientId,
      jwtTenant.opaqueClient.clientSecret
    );
    expectOpaque(accessToken);
  });

  it("tenant default opaque, client without override: access token falls back to opaque", async () => {
    const accessToken = await issueAccessToken(
      opaqueTenant,
      opaqueTenant.clientAId,
      opaqueTenant.clientASecret
    );
    expectOpaque(accessToken);
  });

  it("tenant default opaque, client overrides to JWT: access token is JWT", async () => {
    const accessToken = await issueAccessToken(
      opaqueTenant,
      opaqueTenant.jwtClient.clientId,
      opaqueTenant.jwtClient.clientSecret
    );
    await expectJwt(opaqueTenant, accessToken);
  });
});
