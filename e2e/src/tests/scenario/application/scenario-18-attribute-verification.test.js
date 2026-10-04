import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { deletion, get, patchWithJson, postWithJson } from "../../../lib/http";
import {
  requestToken,
  getAuthorizations,
  postAuthentication,
  authorize,
} from "../../../api/oauthClient";
import { generateECP256JWKS, verifyAndDecodeJwt } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import {
  convertNextAction,
  convertToAuthorizationResponse,
} from "../../../lib/util";

/**
 * Issue #1907: 属性照合（attribute-verification）。
 *
 * パスワードで利用者を確定したあと、生年月日と電話番号の下 4 桁を登録値と照合する。
 * - 一致すれば認可まで進み、amr には照合が入らない（認証要素に数えない）
 * - 不一致は項目を明かさずに attribute_mismatch
 * - 利用者が確定する前は呼べない
 * - 試行回数は利用者ごとに数え、認可リクエストをやり直しても減らない
 */
describe("Authentication: attribute verification (#1907)", () => {
  let systemAccessToken;
  let mgmtAccessToken;
  let organizationId;
  let tenantId;
  let clientId;
  let clientSecret;
  const redirectUri = "https://app.example.com/callback";
  const MAX_ATTEMPTS = 3;

  const checkAccount = { interaction: "identity-verified" };
  const correctAnswer = {
    interaction: "kba",
    birthdate: "2000年1月5日",
    phone_last4: "５６７８",
  };
  const wrongAnswer = {
    interaction: "kba",
    birthdate: "2000/1/6",
    phone_last4: "5678",
  };

  beforeAll(async () => {
    const timestamp = Date.now();
    organizationId = uuidv4();
    tenantId = uuidv4();
    clientId = uuidv4();
    clientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();
    const adminEmail = `admin-${timestamp}@attr-verify.example.com`;
    const adminPassword = `AdminPass_${timestamp}!`;

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

    const onboardingResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: {
        organization: {
          id: organizationId,
          name: `Attribute Verification Org ${timestamp}`,
          description: "E2E for #1907",
        },
        tenant: {
          id: tenantId,
          name: `Attribute Verification Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
          jwks: jwksContent,
          scopes_supported: [
            "openid",
            "profile",
            "email",
            "management",
            "org-management",
          ],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          grant_types_supported: ["authorization_code", "password"],
          id_token_signing_alg_values_supported: ["ES256"],
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          claims_supported: [
            "sub",
            "iss",
            "auth_time",
            "acr",
            "amr",
            "name",
            "email",
            "email_verified",
          ],
          extension: { access_token_type: "JWT" },
        },
        user: {
          sub: uuidv4(),
          provider_id: "idp-server",
          name: "Admin User",
          email: adminEmail,
          email_verified: true,
          raw_password: adminPassword,
        },
        client: {
          client_id: clientId,
          client_secret: clientSecret,
          redirect_uris: [redirectUri],
          response_types: ["code"],
          grant_types: ["authorization_code", "password"],
          scope: "openid profile email management org-management",
          client_name: "Attribute Verification Client",
          token_endpoint_auth_method: "client_secret_post",
          application_type: "web",
        },
      },
    });
    expect(onboardingResponse.status).toBe(201);

    const mgmtTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: adminEmail,
      password: adminPassword,
      scope: "management org-management",
      clientId,
      clientSecret,
    });
    expect(mgmtTokenResponse.status).toBe(200);
    mgmtAccessToken = mgmtTokenResponse.data.access_token;

    const configurationsUrl = `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`;

    const passwordConfigResponse = await postWithJson({
      url: configurationsUrl,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        type: "password",
        attributes: {},
        metadata: {},
        interactions: {
          "password-authentication": {
            execution: { function: "password_verification" },
            response: { body_mapping_rules: [] },
          },
        },
      },
    });
    expect(passwordConfigResponse.status).toBe(201);

    const attributeConfigResponse = await postWithJson({
      url: configurationsUrl,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        type: "attribute-verification",
        attributes: {},
        metadata: {},
        interactions: {
          // Checked first, as its own step: the account itself must be identity-verified.
          "identity-verified": {
            execution: {
              details: {
                conditions: {
                  any_of: [
                    [
                      {
                        path: "$.user.status",
                        type: "string",
                        operation: "eq",
                        value: "IDENTITY_VERIFIED",
                      },
                    ],
                  ],
                },
                error: "identity_verification_required",
              },
            },
          },
          // Then what the end-user enters.
          kba: {
            execution: {
              details: {
                fields: [
                  {
                    input: "birthdate",
                    user_attribute: "birthdate",
                    normalize: "date",
                  },
                  {
                    input: "phone_last4",
                    user_attribute: "phone_number",
                    normalize: "digits",
                    suffix_length: 4,
                  },
                ],
                max_attempts: MAX_ATTEMPTS,
                lockout_seconds: 600,
              },
            },
          },
        },
      },
    });
    expect(attributeConfigResponse.status).toBe(201);

    const policyResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        flow: "oauth",
        enabled: true,
        policies: [
          {
            description: "password_and_attribute_verification",
            priority: 1,
            conditions: {},
            available_methods: ["password", "attribute-verification"],
            step_definitions: [
              {
                method: "password",
                order: 1,
                requires_user: false,
                user_identity_source: "username",
              },
              {
                method: "attribute-verification",
                interaction: "identity-verified",
                order: 2,
                requires_user: true,
              },
              {
                method: "attribute-verification",
                interaction: "kba",
                order: 3,
                requires_user: true,
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
                  {
                    path: "$.attribute-verification.interactions.identity-verified.success_count",
                    type: "integer",
                    operation: "gte",
                    value: 1,
                  },
                  {
                    path: "$.attribute-verification.interactions.kba.success_count",
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
    expect(policyResponse.status).toBe(201);
  });

  afterAll(async () => {
    if (mgmtAccessToken) {
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`,
        headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      }).catch(() => {});
    }
    if (systemAccessToken) {
      await deletion({
        url: `${backendUrl}/v1/management/orgs/${organizationId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }
  });

  /**
   * A user with the attributes the verification compares against. Created REGISTERED and moved to
   * the requested status with the management PATCH.
   */
  const createUser = async (status = "IDENTITY_VERIFIED") => {
    const email = `user-${Date.now()}-${crypto
      .randomBytes(4)
      .toString("hex")}@attr-verify.example.com`;
    const password = "UserPass_1!";
    const response = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        sub: uuidv4(),
        provider_id: "idp-server",
        name: "Attribute User",
        email,
        email_verified: true,
        birthdate: "2000-01-05",
        phone_number: "090-1234-5678",
        raw_password: password,
      },
    });
    expect(response.status).toBe(201);
    const sub = response.data.result.sub;
    if (status !== "REGISTERED") {
      const patchResponse = await patchWithJson({
        url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users/${sub}`,
        headers: { Authorization: `Bearer ${mgmtAccessToken}` },
        body: { status },
      });
      expect(patchResponse.status).toBe(200);
    }
    return { email, password, sub };
  };

  const startAuthorization = async () => {
    const response = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId,
      responseType: "code",
      state: `attr-${Date.now()}`,
      scope: "openid profile email",
      redirectUri,
      prompt: "login",
    });
    expect(response.status).toBe(302);
    return convertNextAction(response.headers.location).params.get("id");
  };

  const signIn = async (authId, user) => {
    const response = await postAuthentication({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/password-authentication`,
      id: authId,
      body: { username: user.email, password: user.password },
    });
    expect(response.status).toBe(200);
  };

  const verifyAttributes = (authId, body) =>
    postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/attribute-verification`,
      body,
    });

  it("completes the authorization when the attributes match, without counting as an authentication method", async () => {
    const user = await createUser();
    const authId = await startAuthorization();
    await signIn(authId, user);

    const accountResponse = await verifyAttributes(authId, checkAccount);
    expect(accountResponse.status).toBe(200);
    const verifyResponse = await verifyAttributes(authId, correctAnswer);
    expect(verifyResponse.status).toBe(200);

    const authorizeResponse = await authorize({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      id: authId,
      body: {},
    });
    expect(authorizeResponse.status).toBe(200);
    const { code } = convertToAuthorizationResponse(
      authorizeResponse.data.redirect_uri
    );

    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "authorization_code",
      code,
      redirectUri,
      clientId,
      clientSecret,
    });
    expect(tokenResponse.status).toBe(200);

    const jwksResponse = await get({
      url: `${backendUrl}/${tenantId}/v1/jwks`,
    });
    const { payload } = verifyAndDecodeJwt({
      jwt: tokenResponse.data.id_token,
      jwks: jwksResponse.data,
    });
    expect(payload.sub).toBe(user.sub);
    expect(payload.amr).toContain("password");
    expect(payload.amr).not.toContain("attribute-verification");
  });

  it("does not complete the authorization on the password alone", async () => {
    const user = await createUser();
    const authId = await startAuthorization();
    await signIn(authId, user);

    const authorizeResponse = await authorize({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      id: authId,
      body: {},
    });
    expect(authorizeResponse.status).not.toBe(200);
  });

  it("answers a mismatch without saying which item was wrong", async () => {
    const user = await createUser();
    const authId = await startAuthorization();
    await signIn(authId, user);

    const response = await verifyAttributes(authId, wrongAnswer);
    expect(response.status).toBe(400);
    expect(response.data.error).toBe("attribute_mismatch");
    expect(JSON.stringify(response.data)).not.toContain("birthdate");
  });

  it("tells the view right after sign-in when the account is not identity-verified", async () => {
    const user = await createUser("REGISTERED");
    const authId = await startAuthorization();
    await signIn(authId, user);

    // The account check is its own step, asked for nothing: the view learns at once.
    const response = await verifyAttributes(authId, checkAccount);
    expect(response.status).toBe(400);
    expect(response.data.error).toBe("identity_verification_required");
  });

  it("does not complete when only one of the two checks has passed", async () => {
    const user = await createUser();
    const authId = await startAuthorization();
    await signIn(authId, user);
    expect((await verifyAttributes(authId, checkAccount)).status).toBe(200);

    const authorizeResponse = await authorize({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      id: authId,
      body: {},
    });
    expect(authorizeResponse.status).not.toBe(200);
  });

  it("refuses a request that names no configured interaction", async () => {
    const user = await createUser();
    const authId = await startAuthorization();
    await signIn(authId, user);

    for (const body of [{}, { interaction: "no-such-check" }]) {
      const response = await verifyAttributes(authId, body);
      expect(response.status).toBe(400);
      expect(response.data.error).toBe("invalid_request");
    }
  });

  it("tells the view, in view-data, what each named interaction needs", async () => {
    const authId = await startAuthorization();

    const response = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/view-data`,
      headers: {},
    });
    expect(response.status).toBe(200);
    const interactions =
      response.data.authentication_step_hints["attribute-verification"]
        .interactions;
    expect(interactions["identity-verified"]).toEqual({
      kind: "conditions",
      inputs: [],
    });
    expect(interactions.kba.kind).toBe("fields");
    expect(interactions.kba.inputs.map((input) => input.input)).toEqual([
      "birthdate",
      "phone_last4",
    ]);
    // Settings only: nothing registered for any user is in the hints.
    expect(
      JSON.stringify(response.data.authentication_step_hints)
    ).not.toContain("2000-01-05");
  });

  it("is refused before an earlier step has established the user", async () => {
    const authId = await startAuthorization();

    const response = await verifyAttributes(authId, correctAnswer);
    expect(response.status).toBe(400);
    expect(response.data.error).toBe("invalid_request");
  });

  it("limits attempts per user, and starting a new authorization does not reset the count", async () => {
    const user = await createUser();

    for (let i = 0; i < MAX_ATTEMPTS; i++) {
      const authId = await startAuthorization();
      await signIn(authId, user);
      const response = await verifyAttributes(authId, wrongAnswer);
      expect(response.data.error).toBe("attribute_mismatch");
    }

    const authId = await startAuthorization();
    await signIn(authId, user);
    const response = await verifyAttributes(authId, correctAnswer);
    expect(response.status).toBe(400);
    expect(response.data.error).toBe("too_many_attempts");
  });
});
