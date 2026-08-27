/**
 * Rolling resource indicators out, and rolling them back (#1824).
 *
 * `scope_resource_mapping` decides what an access token names as its audience, and whether a
 * request asking across resources is refused. An operator turning it on is changing what existing
 * clients receive, so what matters is that it is opt-in, that turning it on only affects the scopes
 * it names, and that removing it restores what was there before.
 *
 * The tenant here is configured the way a tenant is before anyone has thought about resources, and
 * the configuration is added and removed through the management API rather than at onboarding.
 */
import { afterAll, beforeAll, describe, expect, it } from "@jest/globals";
import { v4 as uuidv4 } from "uuid";
import { deletion, get, putWithJson } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { onboarding } from "../../../api/managementClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";

const decodePayload = (jwt) =>
  JSON.parse(Buffer.from(jwt.split(".")[1], "base64url").toString());

describe("standard-31: resource indicator rollout", () => {
  let systemAccessToken;
  let organizationId;
  let tenantId;
  let clientId;
  let clientSecret;
  let issuer;
  let managementAccessToken;
  let username;
  let password;

  const ACCOUNT_RESOURCE = "https://api.example.com";
  const MANAGEMENT_RESOURCE = "https://admin.example.com";

  const tokenEndpoint = () => `${backendUrl}/${tenantId}/v1/tokens`;
  const authorizationServerUrl = () =>
    `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authorization-server`;

  /** The organization level endpoints are reached with a token issued by the tenant itself. */
  const managementHeaders = () => ({
    Authorization: `Bearer ${managementAccessToken}`,
  });

  /** Replaces the authorization server extension, keeping everything else as it is. */
  const updateExtension = async (extension) => {
    const current = await get({
      url: authorizationServerUrl(),
      headers: managementHeaders(),
    });
    expect(current.status).toBe(200);

    const response = await putWithJson({
      url: authorizationServerUrl(),
      headers: managementHeaders(),
      body: {
        ...current.data,
        extension: { ...current.data.extension, ...extension },
      },
    });
    expect(response.status).toBe(200);
  };

  const requestClientToken = async (scope) =>
    await requestToken({
      endpoint: tokenEndpoint(),
      grantType: "client_credentials",
      scope,
      clientId,
      clientSecret,
    });

  beforeAll(async () => {
    const timestamp = Date.now();
    organizationId = uuidv4();
    tenantId = uuidv4();
    clientId = uuidv4();
    clientSecret = uuidv4();
    issuer = `${backendUrl}/${tenantId}`;
    username = `resource-indicator-${timestamp}@test.example.com`;
    password = `ResourceIndicator${timestamp}!`;

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
    systemAccessToken = systemTokenResponse.data.access_token;

    const onboardingResponse = await onboarding({
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: {
        organization: {
          id: organizationId,
          name: `Resource Indicator Org ${timestamp}`,
          description: "E2E for #1824",
        },
        tenant: {
          id: tenantId,
          name: `Resource Indicator Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
        },
        authorization_server: {
          issuer,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: tokenEndpoint(),
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
          jwks: await generateECP256JWKS(),
          scopes_supported: ["openid", "account", "management"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          grant_types_supported: [
            "authorization_code",
            "client_credentials",
            "password",
          ],
          id_token_signing_alg_values_supported: ["ES256"],
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          claims_supported: ["sub"],
          // No resources modelled, which is how a tenant looks before the rollout.
          extension: { access_token_type: "JWT" },
        },
        user: {
          sub: uuidv4(),
          provider_id: "idp-server",
          email: username,
          email_verified: true,
          raw_password: password,
        },
        client: {
          client_id: clientId,
          client_secret: clientSecret,
          redirect_uris: ["https://app.example.com/callback"],
          grant_types: ["authorization_code", "client_credentials", "password"],
          response_types: ["code"],
          scope: "openid account management",
          client_name: "Resource Indicator Client",
          token_endpoint_auth_method: "client_secret_post",
        },
      },
    });
    expect(onboardingResponse.status).toBe(201);

    const managementTokenResponse = await requestToken({
      endpoint: tokenEndpoint(),
      grantType: "password",
      username,
      password,
      scope: "management",
      clientId,
      clientSecret,
    });
    expect(managementTokenResponse.status).toBe(200);
    managementAccessToken = managementTokenResponse.data.access_token;
  }, 120000);

  afterAll(async () => {
    if (systemAccessToken) {
      await deletion({
        url: `${backendUrl}/v1/management/orgs/${organizationId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }
  });

  it("before the rollout: any combination of the client's scopes is granted, and the audience is the issuer", async () => {
    // Nothing is configured, so nothing constrains which scopes may be asked for together. The
    // audience still has to be present, because RFC 9068 does not allow it to be omitted.
    const spanning = await requestClientToken("account management");
    expect(spanning.status).toBe(200);

    const payload = decodePayload(spanning.data.access_token);
    console.log("before rollout:", JSON.stringify(payload));

    expect(payload.aud).toBe(issuer);
    expect(payload.scope).toContain("account");
    expect(payload.scope).toContain("management");
  });

  it("after the rollout: a token names the resource its scopes belong to", async () => {
    await updateExtension({
      scope_resource_mapping: {
        [ACCOUNT_RESOURCE]: ["account"],
        [MANAGEMENT_RESOURCE]: ["management"],
      },
    });

    const account = await requestClientToken("account");
    expect(account.status).toBe(200);
    expect(decodePayload(account.data.access_token).aud).toBe(ACCOUNT_RESOURCE);

    const management = await requestClientToken("management");
    expect(management.status).toBe(200);
    expect(decodePayload(management.data.access_token).aud).toBe(
      MANAGEMENT_RESOURCE
    );
  });

  it("after the rollout: a request across the mapped resources is refused", async () => {
    // This is the behaviour change an operator is opting into. It applies only once the scopes have
    // been mapped, which is why the same request succeeded before the rollout.
    const spanning = await requestClientToken("account management");
    console.log(
      "after rollout, spanning:",
      spanning.status,
      JSON.stringify(spanning.data)
    );

    expect(spanning.status).toBe(400);
    expect(spanning.data.error).toBe("invalid_scope");
  });

  it("after the rollout: a scope left out of the mapping is unaffected", async () => {
    // openid is deliberately unmapped, so it neither selects a resource nor drags a request into
    // spanning two. A rollout that named it would make its resource a party to every request.
    const response = await requestClientToken("openid account");
    expect(response.status).toBe(200);

    expect(decodePayload(response.data.access_token).aud).toBe(
      ACCOUNT_RESOURCE
    );
  });

  it("rolling back: removing the mapping restores what clients received before", async () => {
    await updateExtension({ scope_resource_mapping: {} });

    const spanning = await requestClientToken("account management");
    console.log("after rollback:", spanning.status);
    expect(spanning.status).toBe(200);

    const payload = decodePayload(spanning.data.access_token);
    expect(payload.aud).toBe(issuer);
    expect(payload.scope).toContain("account");
    expect(payload.scope).toContain("management");
  });

  it("rolling back to a named default keeps the audience off the issuer", async () => {
    // An operator part way through the rollout may want a resource named without mapping scopes to
    // it yet. The default applies to every token, and asking across scopes is unconstrained again.
    await updateExtension({
      scope_resource_mapping: {},
      default_resource_indicator: ACCOUNT_RESOURCE,
    });

    const spanning = await requestClientToken("account management");
    expect(spanning.status).toBe(200);

    expect(decodePayload(spanning.data.access_token).aud).toBe(
      ACCOUNT_RESOURCE
    );
  });
});
