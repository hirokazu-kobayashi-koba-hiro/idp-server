import { describe, expect, it, beforeAll } from "@jest/globals";
import { get, postWithJson } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { onboarding } from "../../../api/managementClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";

/**
 * Minimal Onboarding: Verify onboarding with minimal fields,
 * then confirm the created tenant is usable (token issuance + management API).
 *
 * Validates:
 * 1. Onboarding succeeds with minimal required fields
 * 2. Password grant token issuance works on the created tenant
 * 3. Tenant management API is accessible with the issued token
 * 4. Dry run works without persisting
 */
describe("Minimal Onboarding", () => {
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

  it("should onboard, issue token via password grant, and access tenant API", async () => {
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const clientId = uuidv4();
    const clientSecret = crypto.randomBytes(32).toString("hex");
    const userEmail = `minimal-${Date.now()}@test.example.com`;
    const userPassword = `MinimalPass${Date.now()}!`;
    const jwksContent = await generateECP256JWKS();

    // --- Step 1: Onboard with minimal + operational fields ---
    console.log("\n=== Step 1: Onboard ===");

    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: "Minimal Org",
        description: "Minimal onboarding test",
      },
      tenant: {
        id: tenantId,
        name: "Minimal Tenant",
        domain: backendUrl,
        authorization_provider: "idp-server",
      },
      authorization_server: {
        issuer: `${backendUrl}/${tenantId}`,
        authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
        token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
        jwks: jwksContent,
        scopes_supported: ["openid", "profile", "email", "management"],
        response_types_supported: ["code"],
        response_modes_supported: ["query"],
        subject_types_supported: ["public"],
        grant_types_supported: ["authorization_code", "password"],
        token_endpoint_auth_methods_supported: ["client_secret_post"],
        extension: {
          access_token_type: "JWT",
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
        },
      },
      user: {
        sub: uuidv4(),
        provider_id: "idp-server",
        email: userEmail,
        raw_password: userPassword,
      },
      client: {
        client_id: clientId,
        client_secret: clientSecret,
        redirect_uris: ["http://localhost:3000/callback"],
        grant_types: ["authorization_code", "password"],
        response_types: ["code"],
        scope: "openid profile email management",
        token_endpoint_auth_method: "client_secret_post",
      },
    };

    const createResponse = await onboarding({
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: onboardingRequest,
    });

    console.log("Onboarding status:", createResponse.status);
    if (createResponse.status !== 201) {
      console.log("Error:", JSON.stringify(createResponse.data, null, 2));
    }

    expect(createResponse.status).toBe(201);
    expect(createResponse.data.dry_run).toBe(false);
    expect(createResponse.data.organization.id).toBe(organizationId);
    expect(createResponse.data.tenant.id).toBe(tenantId);
    expect(createResponse.data.tenant.type).toBe("ORGANIZER");
    expect(createResponse.data.user.sub).toBeDefined();
    expect(createResponse.data.user.status).toBe("REGISTERED");
    expect(createResponse.data.client.client_id).toBe(clientId);

    // --- Step 2: Issue token via password grant ---
    console.log("\n=== Step 2: Password Grant ===");

    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: userEmail,
      password: userPassword,
      scope: "openid management",
      clientId: clientId,
      clientSecret: clientSecret,
    });

    console.log("Token status:", tokenResponse.status);
    if (tokenResponse.status !== 200) {
      console.log("Error:", JSON.stringify(tokenResponse.data, null, 2));
    }

    expect(tokenResponse.status).toBe(200);
    expect(tokenResponse.data.access_token).toBeDefined();
    expect(tokenResponse.data.token_type).toBe("Bearer");

    const orgAccessToken = tokenResponse.data.access_token;

    // --- Step 3: Access tenant API via organization-level endpoint ---
    console.log("\n=== Step 3: Tenant API ===");

    const tenantResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
    });

    console.log("Tenant API status:", tenantResponse.status);
    if (tenantResponse.status !== 200) {
      console.log("Error:", JSON.stringify(tenantResponse.data, null, 2));
    }

    expect(tenantResponse.status).toBe(200);
    expect(tenantResponse.data.id).toBe(tenantId);
    expect(tenantResponse.data.name).toBe("Minimal Tenant");
    expect(tenantResponse.data.type).toBe("ORGANIZER");
  });

  it("should succeed with dry_run=true without persisting", async () => {
    const tenantId = uuidv4();
    const jwksContent = await generateECP256JWKS();

    const response = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding?dry_run=true`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: {
        organization: {
          id: uuidv4(),
          name: "Dry Run Org",
          description: "Dry run test",
        },
        tenant: {
          id: tenantId,
          name: "Dry Run Tenant",
          domain: backendUrl,
          authorization_provider: "idp-server",
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
          jwks: jwksContent,
          scopes_supported: ["openid"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
        },
        user: {
          sub: uuidv4(),
          provider_id: "idp-server",
          email: `dryrun-${Date.now()}@test.example.com`,
          raw_password: `DryRunPass${Date.now()}!`,
        },
        client: {
          client_id: uuidv4(),
          client_secret: crypto.randomBytes(32).toString("hex"),
          redirect_uris: ["http://localhost:3000/callback"],
        },
      },
    });

    expect(response.status).toBe(200);
    expect(response.data.dry_run).toBe(true);

    // Verify tenant was NOT persisted
    const tenantCheck = await get({
      url: `${backendUrl}/v1/management/tenants/${tenantId}`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });
    expect(tenantCheck.status).toBe(404);
  });
});
