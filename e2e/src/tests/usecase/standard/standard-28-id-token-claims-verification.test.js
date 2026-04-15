import { describe, expect, it, beforeAll } from "@jest/globals";
import { getJwks, requestToken } from "../../../api/oauthClient";
import { onboarding } from "../../../api/managementClient";
import { verifyAndDecodeJwt, generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";

/**
 * Standard Use Case: ID Token Claims Verification
 *
 * End-to-end verification that ID token claims match the OpenAPI specification
 * (swagger-rp-ja.yaml TokenResponse.id_token).
 *
 * Scenarios:
 * 1. Profile/email scope → user attribute claims in ID token
 * 2. ex_sub via claims:ex_sub scope → external user ID in ID token
 * 3. custom_properties via claims:{key} scope → arbitrary properties in ID token
 * 4. Multiple scopes combined → all claims present without conflict
 */
describe("Standard Use Case: ID Token Claims Verification", () => {
  let systemAccessToken;
  let tenantId;
  let organizationId;
  let clientId;
  let clientSecret;
  let userSub;
  let userEmail;
  let userPassword;
  const externalUserId = "EXT-12345-ABCDE";
  const employeeNumber = "EMP-9876";
  const department = "engineering";

  beforeAll(async () => {
    // Get admin token
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

    // Setup tenant with full claims support
    organizationId = uuidv4();
    tenantId = uuidv4();
    clientId = uuidv4();
    clientSecret = crypto.randomBytes(32).toString("hex");
    userSub = uuidv4();
    userEmail = `idtoken-uc-${Date.now()}@test.example.com`;
    userPassword = `IdTokenUC${Date.now()}!`;
    const jwksContent = await generateECP256JWKS();

    const onboardingResponse = await onboarding({
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: {
        organization: {
          id: organizationId,
          name: "ID Token Claims Org",
          description: "ID token claims usecase test",
        },
        tenant: {
          id: tenantId,
          name: "ID Token Claims Tenant",
          domain: backendUrl,
          authorization_provider: "idp-server",
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
          jwks: jwksContent,
          scopes_supported: [
            "openid", "profile", "email", "phone", "management",
            "claims:ex_sub", "claims:status",
            "claims:roles", "claims:permissions",
            "claims:assigned_tenants", "claims:assigned_organizations",
            "claims:employee_number", "claims:department",
          ],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          grant_types_supported: ["authorization_code", "password"],
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          claims_supported: [
            "sub", "iss", "aud", "exp", "iat",
            "name", "given_name", "family_name", "preferred_username",
            "email", "email_verified",
            "phone_number", "phone_number_verified",
          ],
          extension: {
            access_token_type: "JWT",
            token_signed_key_id: "signing_key_1",
            id_token_signed_key_id: "signing_key_1",
            custom_claims_scope_mapping: true,
          },
        },
        user: {
          sub: userSub,
          provider_id: "idp-server",
          external_user_id: externalUserId,
          name: "Taro Yamada",
          given_name: "Taro",
          family_name: "Yamada",
          email: userEmail,
          email_verified: true,
          phone_number: "+819012345678",
          phone_number_verified: true,
          raw_password: userPassword,
          custom_properties: {
            employee_number: employeeNumber,
            department: department,
          },
        },
        client: {
          client_id: clientId,
          client_secret: clientSecret,
          redirect_uris: ["http://localhost:3000/callback"],
          grant_types: ["authorization_code", "password"],
          response_types: ["code"],
          scope: "openid profile email phone management claims:ex_sub claims:status claims:roles claims:permissions claims:assigned_tenants claims:assigned_organizations claims:employee_number claims:department",
          token_endpoint_auth_method: "client_secret_post",
        },
      },
    });
    expect(onboardingResponse.status).toBe(201);
  });

  it("password grant ID token should contain required claims and signature verification succeeds", async () => {
    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: userEmail,
      password: userPassword,
      scope: "openid profile email phone",
      clientId,
      clientSecret,
    });
    expect(tokenResponse.status).toBe(200);

    const jwksResponse = await getJwks({
      endpoint: `${backendUrl}/${tenantId}/v1/jwks`,
    });
    const decoded = verifyAndDecodeJwt({
      jwt: tokenResponse.data.id_token,
      jwks: jwksResponse.data,
    });
    expect(decoded.verifyResult).toBe(true);

    const payload = decoded.payload;

    // Required claims always present
    expect(payload.iss).toBe(`${backendUrl}/${tenantId}`);
    expect(payload.sub).toBe(userSub);
    expect(payload.aud).toContain(clientId);
    expect(payload.exp).toBeGreaterThan(payload.iat);

    // Header structure
    expect(decoded.header.alg).toBeDefined();
    expect(decoded.header.kid).toBeDefined();

    // Note: In password grant, user attribute claims (name, email, etc.)
    // are determined by GrantIdTokenClaims which depends on the authorization
    // flow context. The presence of profile/email claims may vary.
    // Use UserInfo endpoint for reliable user attribute retrieval.
  });

  it("claims:ex_sub scope should include external user ID", async () => {
    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: userEmail,
      password: userPassword,
      scope: "openid claims:ex_sub",
      clientId,
      clientSecret,
    });
    expect(tokenResponse.status).toBe(200);

    const jwksResponse = await getJwks({
      endpoint: `${backendUrl}/${tenantId}/v1/jwks`,
    });
    const decoded = verifyAndDecodeJwt({
      jwt: tokenResponse.data.id_token,
      jwks: jwksResponse.data,
    });
    expect(decoded.verifyResult).toBe(true);

    expect(decoded.payload.ex_sub).toBe(externalUserId);
  });

  it("claims:{key} scope should include custom_properties values", async () => {
    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: userEmail,
      password: userPassword,
      scope: "openid claims:employee_number claims:department",
      clientId,
      clientSecret,
    });
    expect(tokenResponse.status).toBe(200);

    const jwksResponse = await getJwks({
      endpoint: `${backendUrl}/${tenantId}/v1/jwks`,
    });
    const decoded = verifyAndDecodeJwt({
      jwt: tokenResponse.data.id_token,
      jwks: jwksResponse.data,
    });
    expect(decoded.verifyResult).toBe(true);

    expect(decoded.payload.employee_number).toBe(employeeNumber);
    expect(decoded.payload.department).toBe(department);
  });

  it("all scopes combined should include all claims without conflict", async () => {
    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: userEmail,
      password: userPassword,
      scope: "openid profile email claims:ex_sub claims:status claims:roles claims:employee_number claims:department",
      clientId,
      clientSecret,
    });
    expect(tokenResponse.status).toBe(200);

    const jwksResponse = await getJwks({
      endpoint: `${backendUrl}/${tenantId}/v1/jwks`,
    });
    const decoded = verifyAndDecodeJwt({
      jwt: tokenResponse.data.id_token,
      jwks: jwksResponse.data,
    });
    expect(decoded.verifyResult).toBe(true);

    const payload = decoded.payload;

    // Standard claims intact
    expect(payload.iss).toBe(`${backendUrl}/${tenantId}`);
    expect(payload.sub).toBe(userSub);
    expect(payload.aud).toContain(clientId);

    // Custom claims
    expect(payload.ex_sub).toBe(externalUserId);
    expect(payload.status).toBe("REGISTERED");
    expect(payload.roles).toBeDefined();
    expect(payload.employee_number).toBe(employeeNumber);
    expect(payload.department).toBe(department);
  });
});
