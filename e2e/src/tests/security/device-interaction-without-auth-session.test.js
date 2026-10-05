import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import axios from "axios";
import { getAuthorizations, requestToken } from "../../api/oauthClient";
import {
  backendUrl,
  clientSecretPostClient,
  serverConfig,
} from "../testConfig";
import { get, postWithJson, deletion } from "../../lib/http";
import { faker } from "@faker-js/faker";
import { v4 as uuidv4 } from "uuid";
import { generateRS256KeyPair } from "../../lib/jose";
import { convertNextAction } from "../../lib/util";

/**
 * Issue #1940: 認証デバイスから送るインタラクションは、AUTH_SESSION cookie を持たない。
 *
 * 認証ポリシーで auth_session_binding_required: true にしていても、デバイス側の
 * POST /v1/authentications/{transaction-id}/{interaction-type} は cookie なしで通らなければならない。
 *
 * lib/http はプロセス全体で 1 つの cookie jar を共有していて、デバイス向けのリクエストにも
 * ブラウザの cookie が載ってしまう。ここではデバイス側の呼び出しを cookie を持たないクライアントで送る。
 */
describe("Security: device interactions are not bound to the browser's AUTH_SESSION (#1940)", () => {
  let adminAccessToken;
  let tenantId;
  let clientId;
  let clientSecret;
  let userEmail;
  const testPassword = "DeviceCancel123!";
  const redirectUri = "http://localhost:8080/callback";
  const scope = "openid profile email";

  /** 認証デバイスに見立てる、cookie を持たないクライアント。 */
  const device = axios.create({ maxRedirects: 0, validateStatus: () => true });

  beforeAll(async () => {
    const adminTokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      grantType: "password",
      username: serverConfig.oauth.username,
      password: serverConfig.oauth.password,
      scope: clientSecretPostClient.scope,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });
    expect(adminTokenResponse.status).toBe(200);
    adminAccessToken = adminTokenResponse.data.access_token;

    tenantId = uuidv4();
    const { jwks } = await generateRS256KeyPair();
    const createTenantResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        tenant: {
          id: tenantId,
          name: "Device interaction without AUTH_SESSION Tenant",
          domain: backendUrl,
          authorization_provider: "idp-server",
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/.well-known/jwks.json`,
          jwks: jwks,
          scopes_supported: ["openid", "profile", "email"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          grant_types_supported: ["authorization_code"],
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          id_token_signing_alg_values_supported: ["RS256"],
          claims_supported: [
            "sub",
            "name",
            "email",
            "email_verified",
            "preferred_username",
          ],
          extension: {
            access_token_type: "JWT",
            access_token_duration: 3600,
            id_token_duration: 3600,
          },
        },
      },
    });
    expect(createTenantResponse.status).toBe(201);

    clientId = uuidv4();
    clientSecret = uuidv4();
    const createClientResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/clients`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        client_id: clientId,
        client_secret: clientSecret,
        redirect_uris: [redirectUri],
        grant_types: ["authorization_code"],
        response_types: ["code"],
        scope: scope,
        token_endpoint_auth_method: "client_secret_post",
      },
    });
    expect(createClientResponse.status).toBe(201);

    // password をブラウザで、2 段目をデバイスで行うポリシー。2 段目の成功はここでは使わず、
    // デバイスからの拒否・取り消しで failure になることだけを見る。
    const createPolicyResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: uuidv4(),
        flow: "oauth",
        enabled: true,
        policies: [
          {
            description: "password_then_device",
            priority: 100,
            conditions: { scopes: ["openid"] },
            available_methods: ["password", "fido-uaf"],
            auth_session_binding_required: true,
            success_conditions: {
              any_of: [
                [
                  {
                    path: "$.fido-uaf-authentication.success_count",
                    type: "integer",
                    operation: "gte",
                    value: 1,
                  },
                ],
              ],
            },
            failure_conditions: {
              any_of: [
                [
                  {
                    path: "$.authentication-cancel.success_count",
                    type: "integer",
                    operation: "gte",
                    value: 1,
                  },
                ],
                [
                  {
                    path: "$.authentication-device-deny.success_count",
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
    expect(createPolicyResponse.status).toBe(201);

    userEmail = faker.internet.email().toLowerCase();
    const createUserResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/users`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        sub: uuidv4(),
        provider_id: "idp-server",
        name: "Device Cancel User",
        email: userEmail,
        preferred_username: userEmail,
        raw_password: testPassword,
      },
    });
    expect(createUserResponse.status).toBe(201);
  }, 60000);

  afterAll(async () => {
    if (!adminAccessToken || !tenantId) {
      return;
    }
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
    }).catch(() => {});
  });

  /** ブラウザで認可を始めて password まで進め、デバイスが受け取る認証トランザクション ID を返す。 */
  const startFlowUntilDeviceStep = async () => {
    const authResponse = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId: clientId,
      responseType: "code",
      state: `dc-${Date.now()}-${Math.random()}`,
      scope: scope,
      redirectUri: redirectUri,
    });
    expect(authResponse.status).toBe(302);
    const { params } = convertNextAction(authResponse.headers.location);
    const authId = params.get("id");

    const passwordResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/password-authentication`,
      body: { username: userEmail, password: testPassword },
    });
    expect(passwordResponse.status).toBe(200);

    const transactionsResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/authentication-transactions?authorization_id=${authId}`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
    });
    expect(transactionsResponse.status).toBe(200);
    const transactionId = transactionsResponse.data.list[0].id;

    return { authId, transactionId };
  };

  const authenticationStatus = async (authId) => {
    const response = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/authentication-status`,
    });
    expect(response.status).toBe(200);
    return response.data.status;
  };

  const sendFromDevice = (transactionId, interactionType) =>
    device.post(
      `${backendUrl}/${tenantId}/v1/authentications/${transactionId}/${interactionType}`,
      {}
    );

  it("前提: ブラウザ側のインタラクションは、cookie がなければ拒否される", async () => {
    const authResponse = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId: clientId,
      responseType: "code",
      state: `dc-${Date.now()}-${Math.random()}`,
      scope: scope,
      redirectUri: redirectUri,
    });
    expect(authResponse.status).toBe(302);
    const { params } = convertNextAction(authResponse.headers.location);
    const authId = params.get("id");

    const response = await device.post(
      `${backendUrl}/${tenantId}/v1/authorizations/${authId}/password-authentication`,
      { username: userEmail, password: testPassword }
    );

    expect(response.status).toBe(401);
  }, 90000);

  it("authentication-device-deny はデバイスから cookie なしで送れ、認証は failure になる", async () => {
    const { authId, transactionId } = await startFlowUntilDeviceStep();

    const response = await sendFromDevice(transactionId, "authentication-device-deny");

    expect(response.status).toBe(200);
    expect(await authenticationStatus(authId)).toBe("failure");
  }, 90000);

  it("authentication-cancel はデバイスから cookie なしで送れ、認証は failure になる", async () => {
    const { authId, transactionId } = await startFlowUntilDeviceStep();

    const response = await sendFromDevice(transactionId, "authentication-cancel");

    expect(response.status).toBe(200);
    expect(await authenticationStatus(authId)).toBe("failure");
  }, 90000);
});
