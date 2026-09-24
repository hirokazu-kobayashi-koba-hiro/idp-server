import { describe, expect, it, beforeAll } from "@jest/globals";
import { onboarding } from "../../api/managementClient";
import { postWithJson } from "../../lib/http";
import { requestToken } from "../../api/oauthClient";
import { generateECP256JWKS } from "../../lib/jose";
import { adminServerConfig, backendUrl } from "../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import { createBearerHeader } from "../../lib/util";

/**
 * `POST /{tenant-id}/v1/me/mfa/{mfa-operation-type}` turns the path segment into an AuthFlow
 * verbatim (`UserV1Api`) and `UserOperationEntryService.requestMfaOperation` validates nothing:
 * `MfaRegistrationVerifiers.get` returns an always-success no-op for any flow outside its two
 * FIDO-UAF entries. So any flow name with a registered authentication policy could be minted from
 * there with a bare user token.
 *
 * For `oauth` / `ciba` that produces an orphan: `MfaRegistrationTransactionCreator` leaves the
 * `AuthorizationIdentifier` empty, and the flow-specific entry service looks up the authorization /
 * backchannel request before running any interactor. Observed before the fix:
 *
 *   POST /v1/me/mfa/oauth                                   -> 200 {"id": ...}
 *   POST /v1/authentications/{id}/password-authentication    -> 500 server_error
 *
 * i.e. any user token could create orphan transaction rows and turn user input into a 500. The
 * minter now refuses these flows with 400.
 *
 * The policies are registered first on purpose: without them the minter 404s on the policy lookup,
 * which would hide whether the flow itself is refused.
 */
describe("Security: the generic MFA minter refuses non-MFA flows", () => {
  let ctx;

  const loginPolicy = (flow) => ({
    id: uuidv4(),
    flow,
    enabled: true,
    policies: [
      {
        description: "password_login",
        priority: 1,
        conditions: {},
        available_methods: ["password"],
        step_definitions: [{ method: "password", order: 1, requires_user: false }],
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
  });

  beforeAll(async () => {
    const systemTokenResponse = await requestToken({
      endpoint: adminServerConfig.tokenEndpoint,
      grantType: "password",
      username: adminServerConfig.oauth.username,
      password: adminServerConfig.oauth.password,
      scope: adminServerConfig.adminClient.scope,
      clientId: adminServerConfig.adminClient.clientId,
      clientSecret: adminServerConfig.adminClient.clientSecret,
    });
    expect(systemTokenResponse.status).toBe(200);
    const systemAccessToken = systemTokenResponse.data.access_token;

    const timestamp = `${Date.now()}-${crypto.randomBytes(3).toString("hex")}`;
    ctx = {
      organizationId: uuidv4(),
      tenantId: uuidv4(),
      clientId: uuidv4(),
      clientSecret: `client-secret-${crypto.randomBytes(16).toString("hex")}`,
      adminName: `admin-${timestamp}`,
      adminEmail: `admin-${timestamp}@mfa-minter.example.com`,
      adminPassword: `AdminPass_${timestamp}!`,
    };
    const jwksContent = await generateECP256JWKS();

    const onboardingResponse = await onboarding({
      body: {
        organization: { id: ctx.organizationId, name: `Minter Org ${timestamp}`, description: "e2e" },
        tenant: {
          id: ctx.tenantId,
          name: `Minter Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          session_config: { cookie_name: `MINTER_${timestamp}`, use_secure_cookie: false },
          cors_config: { allow_origins: [backendUrl] },
        },
        authorization_server: {
          issuer: `${backendUrl}/${ctx.tenantId}`,
          authorization_endpoint: `${backendUrl}/${ctx.tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${ctx.tenantId}/v1/tokens`,
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          userinfo_endpoint: `${backendUrl}/${ctx.tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${ctx.tenantId}/v1/jwks`,
          jwks: jwksContent,
          grant_types_supported: ["authorization_code", "refresh_token", "password"],
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          scopes_supported: ["openid", "profile", "email", "management", "org-management"],
          claims_supported: ["sub", "iss", "name", "email"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          id_token_signing_alg_values_supported: ["ES256"],
          extension: { access_token_type: "JWT" },
        },
        user: {
          sub: uuidv4(),
          provider_id: "idp-server",
          name: ctx.adminName,
          preferred_username: ctx.adminEmail,
          email: ctx.adminEmail,
          email_verified: true,
          raw_password: ctx.adminPassword,
        },
        client: {
          client_id: ctx.clientId,
          client_secret: ctx.clientSecret,
          redirect_uris: [`${backendUrl}/callback`],
          response_types: ["code"],
          grant_types: ["authorization_code", "refresh_token", "password"],
          scope: "openid profile email management org-management",
          client_name: "Minter Client",
          token_endpoint_auth_method: "client_secret_post",
          application_type: "web",
        },
      },
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });
    expect(onboardingResponse.status).toBe(201);

    const mgmtTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${ctx.tenantId}/v1/tokens`,
      grantType: "password",
      username: ctx.adminEmail,
      password: ctx.adminPassword,
      scope: "management org-management",
      clientId: ctx.clientId,
      clientSecret: ctx.clientSecret,
    });
    expect(mgmtTokenResponse.status).toBe(200);
    ctx.mgmtAccessToken = mgmtTokenResponse.data.access_token;

    for (const flow of ["oauth", "ciba"]) {
      const policyResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${ctx.organizationId}/tenants/${ctx.tenantId}/authentication-policies`,
        headers: { Authorization: `Bearer ${ctx.mgmtAccessToken}` },
        body: loginPolicy(flow),
      });
      expect(policyResponse.status).toBe(201);
    }
  });

  it("refuses oauth / ciba even though both have a registered authentication policy", async () => {
    const token = (
      await requestToken({
        endpoint: `${backendUrl}/${ctx.tenantId}/v1/tokens`,
        grantType: "password",
        username: ctx.adminEmail,
        password: ctx.adminPassword,
        scope: "openid",
        clientId: ctx.clientId,
        clientSecret: ctx.clientSecret,
      })
    ).data.access_token;

    for (const flow of ["oauth", "ciba"]) {
      const response = await postWithJson({
        url: `${backendUrl}/${ctx.tenantId}/v1/me/mfa/${flow}`,
        headers: createBearerHeader(token),
        body: {},
      });
      console.log(`mfa minter ${flow}:`, response.status, JSON.stringify(response.data));
      expect(response.status).toBe(400);
      expect(response.data.error_description).toContain("not an MFA operation");
      // No orphan transaction was created, so there is no id to drive into a 500.
      expect(response.data.id).toBeUndefined();
    }
  });

  it("still allows a genuine MFA registration flow", async () => {
    const token = (
      await requestToken({
        endpoint: `${backendUrl}/${ctx.tenantId}/v1/tokens`,
        grantType: "password",
        username: ctx.adminEmail,
        password: ctx.adminPassword,
        scope: "openid",
        clientId: ctx.clientId,
        clientSecret: ctx.clientSecret,
      })
    ).data.access_token;

    // No fido2-registration policy on this tenant, so the mint fails at the policy lookup (404) —
    // NOT at the deny check. The distinction is the point: the deny set must not swallow MFA flows.
    const response = await postWithJson({
      url: `${backendUrl}/${ctx.tenantId}/v1/me/mfa/fido2-registration`,
      headers: createBearerHeader(token),
      body: {},
    });
    console.log("mfa minter fido2-registration:", response.status, JSON.stringify(response.data));
    expect(response.status).toBe(404);
    expect(response.data.error_description).not.toContain("not an MFA operation");
  });
});
