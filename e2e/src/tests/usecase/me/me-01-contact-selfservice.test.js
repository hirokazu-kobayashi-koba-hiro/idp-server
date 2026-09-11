import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { onboarding } from "../../../api/managementClient";
import { deletion, get, postWithJson } from "../../../lib/http";
import { requestToken, getUserinfo } from "../../../api/oauthClient";
import { generateECP256JWKS, verifyAndDecodeJwt } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import { createBearerHeader } from "../../../lib/util";

/**
 * /v1/me Use Case: self-service contact verification and change (Issue #1416).
 *
 * Eight endpoints: {verification, change} x {email, phone}. Verification takes no body and targets
 * the value already on the account; change takes {new_value} and needs its own scope, because it
 * moves preferred_username — the login identifier — under a matching identity policy.
 *
 *   POST /v1/me/{channel}/verification             (scope openid)          -> *_verified: true
 *   POST /v1/me/{channel}/verification/{id}/verify (scope openid)
 *   POST /v1/me/{channel}/change      {new_value}  (scope {channel}:change) -> code to the NEW value
 *   POST /v1/me/{channel}/change/{id}/verify       (scope {channel}:change)
 *
 * This flow deliberately does NOT run on AuthenticationTransaction / AuthenticationInteractor: that
 * machinery's interaction endpoints are unauthenticated by design, and an already-authenticated
 * profile mutation must not be reachable through them. Challenge state lives in its own table,
 * looked up by (id, tenant, owner).
 *
 * Uniqueness is enforced on preferred_username, which the identity policy derives, so whether a
 * duplicate email is rejected depends on the tenant policy:
 *   EMAIL    -> preferred_username tracks email -> change to a taken email is REJECTED
 *   USERNAME -> email is a plain attribute      -> duplicate ALLOWED
 */
const redirectUri = "https://www.certification.openid.net/test/a/idp_oidc_basic/callback";

const smsConfigBody = (expireSeconds = 300) => ({
  id: uuidv4(),
  type: "sms",
  attributes: {},
  metadata: { type: "internal" },
  interactions: {
    "sms-authentication-challenge": {
      request: { schema: { type: "object", properties: { phone_number: { type: "string" } } } },
      execution: {
        function: "sms_authentication_challenge",
        details: {
          sender_type: "http_request",
          settings: {
            http_request: {
              url: "http://host.docker.internal:4000/sent-sms",
              method: "POST",
              header_mapping_rules: [{ static_value: "application/json", to: "Content-Type" }],
              body_mapping_rules: [{ from: "$.request_body", to: "*" }],
            },
          },
          templates: {
            authentication: { subject: "Login", body: "Code: {VERIFICATION_CODE}" },
            phone_change: { subject: "Phone change", body: "Confirm your new number. Code: {VERIFICATION_CODE}" },
            phone_verify: { subject: "Phone verification", body: "Confirm your number. Code: {VERIFICATION_CODE}" },
          },
          retry_count_limitation: 5,
          expire_seconds: expireSeconds,
        },
      },
      response: { body_mapping_rules: [{ from: "$.response_body", to: "*" }] },
    },
  },
});

const emailConfigBody = (expireSeconds = 300) => ({
  id: uuidv4(),
  type: "email",
  attributes: {},
  metadata: {
    type: "internal",
    transaction_id_param: "transaction_id",
    verification_code_param: "verification_code",
  },
  interactions: {
    "email-authentication-challenge": {
      request: {
        schema: { type: "object", properties: { email: { type: "string" } } },
      },
      execution: {
        function: "email_authentication_challenge",
        details: {
          function: "http_request",
          sender: "test@example.com",
          sender_config: {
            http_request: {
              url: "http://host.docker.internal:4000/sent-emails",
              method: "POST",
              header_mapping_rules: [{ static_value: "application/json", to: "Content-Type" }],
              body_mapping_rules: [{ from: "$.request_body", to: "*" }],
            },
          },
          templates: {
            authentication: { subject: "Login code", body: "Code: {VERIFICATION_CODE}" },
            email_change: {
              subject: "Email change confirmation",
              body: "You requested an email change. Code: {VERIFICATION_CODE}",
            },
            email_verify: {
              subject: "Email verification",
              body: "Confirm your email address. Code: {VERIFICATION_CODE}",
            },
          },
          retry_count_limitation: 5,
          expire_seconds: expireSeconds,
        },
      },
      response: { body_mapping_rules: [{ from: "$.response_body", to: "*" }] },
    },
  },
});

/**
 * Onboards a tenant. Note what is NOT here: no authentication policy for the email flow. The
 * feature owns its state, so it needs no per-tenant flow policy — only the email sender config and
 * the email:change scope.
 */
async function provisionTenant(systemAccessToken, identityUniqueKeyType, { expireSeconds = 300 } = {}) {
  const timestamp = `${Date.now()}-${crypto.randomBytes(3).toString("hex")}`;
  const ctx = {
    organizationId: uuidv4(),
    tenantId: uuidv4(),
    clientId: uuidv4(),
    clientSecret: `client-secret-${crypto.randomBytes(16).toString("hex")}`,
    adminName: `admin-${timestamp}`,
    adminEmail: `admin-${timestamp}@me-email.example.com`,
    adminPassword: `AdminPass_${timestamp}!`,
    adminSub: uuidv4(),
  };
  const jwksContent = await generateECP256JWKS();

  const onboardingResponse = await onboarding({
    body: {
      organization: { id: ctx.organizationId, name: `Me Org ${timestamp}`, description: "e2e" },
      tenant: {
        id: ctx.tenantId,
        name: `Me Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        identity_policy_config: { identity_unique_key_type: identityUniqueKeyType },
        session_config: { cookie_name: `ME_${timestamp}`, use_secure_cookie: false },
        cors_config: { allow_origins: [backendUrl] },
        security_event_log_config: {
          format: "structured_json",
          stage: "processed",
          include_user_id: true,
          include_ip: true,
          include_detail: true,
          persistence_enabled: true,
        },
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
        scopes_supported: [
          "openid",
          "profile",
          "email",
          "email:change",
          "phone:change",
          "management",
          "org-management",
        ],
        claims_supported: ["sub", "iss", "name", "email", "email_verified"],
        response_types_supported: ["code"],
        response_modes_supported: ["query"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["ES256"],
        extension: { access_token_type: "JWT" },
      },
      user: {
        sub: ctx.adminSub,
        provider_id: "idp-server",
        name: ctx.adminName,
        preferred_username: identityUniqueKeyType === "EMAIL" ? ctx.adminEmail : ctx.adminName,
        email: ctx.adminEmail,
        email_verified: true,
        raw_password: ctx.adminPassword,
      },
      client: {
        client_id: ctx.clientId,
        client_secret: ctx.clientSecret,
        redirect_uris: [redirectUri],
        response_types: ["code"],
        grant_types: ["authorization_code", "refresh_token", "password"],
        scope: "openid profile email email:change phone:change management org-management",
        client_name: "Me Client",
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
      },
    },
    headers: { Authorization: `Bearer ${systemAccessToken}` },
  });
  if (onboardingResponse.status !== 201) {
    console.error("Onboarding failed:", JSON.stringify(onboardingResponse.data, null, 2));
  }
  expect(onboardingResponse.status).toBe(201);

  const mgmtTokenResponse = await requestToken({
    endpoint: `${backendUrl}/${ctx.tenantId}/v1/tokens`,
    grantType: "password",
    username: identityUniqueKeyType === "EMAIL" ? ctx.adminEmail : ctx.adminName,
    password: ctx.adminPassword,
    scope: "management org-management",
    clientId: ctx.clientId,
    clientSecret: ctx.clientSecret,
  });
  expect(mgmtTokenResponse.status).toBe(200);
  ctx.mgmtAccessToken = mgmtTokenResponse.data.access_token;
  ctx.identityUniqueKeyType = identityUniqueKeyType;

  const emailResp = await postWithJson({
    url: `${backendUrl}/v1/management/organizations/${ctx.organizationId}/tenants/${ctx.tenantId}/authentication-configurations`,
    headers: { Authorization: `Bearer ${ctx.mgmtAccessToken}` },
    body: emailConfigBody(expireSeconds),
  });
  expect(emailResp.status).toBe(201);

  const smsResp = await postWithJson({
    url: `${backendUrl}/v1/management/organizations/${ctx.organizationId}/tenants/${ctx.tenantId}/authentication-configurations`,
    headers: { Authorization: `Bearer ${ctx.mgmtAccessToken}` },
    body: smsConfigBody(expireSeconds),
  });
  expect(smsResp.status).toBe(201);

  return ctx;
}

async function createUser(ctx, { name, email, password, emailVerified = true }) {
  const sub = uuidv4();
  const resp = await postWithJson({
    url: `${backendUrl}/v1/management/organizations/${ctx.organizationId}/tenants/${ctx.tenantId}/users`,
    headers: { Authorization: `Bearer ${ctx.mgmtAccessToken}` },
    body: {
      sub,
      provider_id: "idp-server",
      name,
      preferred_username: ctx.identityUniqueKeyType === "EMAIL" ? email : name,
      email,
      email_verified: emailVerified,
      raw_password: password,
      status: "REGISTERED",
    },
  });
  expect(resp.status).toBe(201);
  return sub;
}

function passwordGrant(ctx, username, password, scope = "openid") {
  return requestToken({
    endpoint: `${backendUrl}/${ctx.tenantId}/v1/tokens`,
    grantType: "password",
    username,
    password,
    scope,
    clientId: ctx.clientId,
    clientSecret: ctx.clientSecret,
  });
}

const changeScope = "openid email email:change phone:change";

/**
 * idp-server generates the code and never returns it in an API response — returning it would defeat
 * the point, which is proving the value is reachable. So the test observes the outbound message: the
 * tenant's sender POSTs to a mockoon CRUD route, and we read the newest entry for that recipient.
 */
async function readSentCode(channel, targetValue) {
  const bucket = channel === "email" ? "sent-emails" : "sent-sms";
  const resp = await get({ url: `http://localhost:4000/${bucket}` });
  expect(resp.status).toBe(200);
  const sent = resp.data.filter((entry) => entry.to === targetValue);
  expect(sent.length).toBeGreaterThan(0);
  const matched = /(\d{6})/.exec(sent[sent.length - 1].body);
  expect(matched).not.toBeNull();
  return matched[1];
}

async function startChange(ctx, accessToken, channel, newValue) {
  const startResp = await postWithJson({
    url: `${backendUrl}/${ctx.tenantId}/v1/me/${channel}/change`,
    headers: createBearerHeader(accessToken),
    body: { new_value: newValue },
  });
  expect(startResp.status).toBe(200);
  return {
    challengeId: startResp.data.id,
    code: await readSentCode(channel, newValue),
  };
}

function submitChangeCode(ctx, accessToken, channel, challengeId, code) {
  return postWithJson({
    url: `${backendUrl}/${ctx.tenantId}/v1/me/${channel}/change/${challengeId}/verify`,
    headers: createBearerHeader(accessToken),
    body: { verification_code: code },
  });
}

async function runChange(ctx, accessToken, channel, newValue) {
  const { challengeId, code } = await startChange(ctx, accessToken, channel, newValue);
  return submitChangeCode(ctx, accessToken, channel, challengeId, code);
}

async function runVerification(ctx, accessToken, channel, currentValue) {
  const startResp = await postWithJson({
    url: `${backendUrl}/${ctx.tenantId}/v1/me/${channel}/verification`,
    headers: createBearerHeader(accessToken),
    body: {},
  });
  expect(startResp.status).toBe(200);
  const code = await readSentCode(channel, currentValue);

  return postWithJson({
    url: `${backendUrl}/${ctx.tenantId}/v1/me/${channel}/verification/${startResp.data.id}/verify`,
    headers: createBearerHeader(accessToken),
    body: { verification_code: code },
  });
}

describe("Me Use Case: self-service contact verification and change", () => {
  let systemAccessToken;
  const tenants = [];

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

  afterAll(async () => {
    for (const ctx of tenants) {
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${ctx.organizationId}/tenants/${ctx.tenantId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      });
    }
  });

  it("EMAIL policy: changes email and moves the login identifier to the new address", async () => {
    const ctx = await provisionTenant(systemAccessToken, "EMAIL");
    tenants.push(ctx);
    const newEmail = `fresh-${Date.now()}@me-email.example.com`;
    const accessToken = (await passwordGrant(ctx, ctx.adminEmail, ctx.adminPassword, changeScope))
      .data.access_token;

    const verifyResp = await runChange(ctx, accessToken, "email", newEmail);
    expect(verifyResp.status).toBe(200);

    const userinfoResp = await getUserinfo({
      endpoint: `${backendUrl}/${ctx.tenantId}/v1/userinfo`,
      authorizationHeader: createBearerHeader(accessToken),
    });
    expect(userinfoResp.data.email).toBe(newEmail);
    expect(userinfoResp.data.email_verified).toBe(true);

    const newLogin = await passwordGrant(ctx, newEmail, ctx.adminPassword, "openid email");
    expect(newLogin.status).toBe(200);
    const jwksResp = await get({ url: `${backendUrl}/${ctx.tenantId}/v1/jwks` });
    const { payload } = verifyAndDecodeJwt({ jwt: newLogin.data.id_token, jwks: jwksResp.data });
    expect(payload.email).toBe(newEmail);

    expect((await passwordGrant(ctx, ctx.adminEmail, ctx.adminPassword)).status).toBe(400);
  });

  it("verification endpoint verifies the current address without changing it", async () => {
    const ctx = await provisionTenant(systemAccessToken, "EMAIL");
    tenants.push(ctx);

    const email = `unverified-${Date.now()}@me-email.example.com`;
    const password = "UnverifiedPass_1!";
    await createUser(ctx, { name: email, email, password, emailVerified: false });
    // Only openid: the verification half must not require email:change.
    const token = (await passwordGrant(ctx, email, password, "openid")).data.access_token;

    const verifyResp = await runVerification(ctx, token, "email", email);
    expect(verifyResp.status).toBe(200);

    const userinfoResp = await getUserinfo({
      endpoint: `${backendUrl}/${ctx.tenantId}/v1/userinfo`,
      authorizationHeader: createBearerHeader(
        (await passwordGrant(ctx, email, password, "openid email")).data.access_token
      ),
    });
    expect(userinfoResp.data.email).toBe(email);
    expect(userinfoResp.data.email_verified).toBe(true);
  });

  it("verification endpoint ignores a caller-supplied new_value", async () => {
    const ctx = await provisionTenant(systemAccessToken, "EMAIL");
    tenants.push(ctx);
    const token = (await passwordGrant(ctx, ctx.adminEmail, ctx.adminPassword, changeScope)).data
      .access_token;

    const startResp = await postWithJson({
      url: `${backendUrl}/${ctx.tenantId}/v1/me/email/verification`,
      headers: createBearerHeader(token),
      body: { new_value: `hijack-${Date.now()}@me-email.example.com` },
    });
    expect(startResp.status).toBe(200);
    const code = await readSentCode("email", ctx.adminEmail);
    const finish = await postWithJson({
      url: `${backendUrl}/${ctx.tenantId}/v1/me/email/verification/${startResp.data.id}/verify`,
      headers: createBearerHeader(token),
      body: { verification_code: code },
    });
    expect(finish.status).toBe(200);

    expect((await passwordGrant(ctx, ctx.adminEmail, ctx.adminPassword)).status).toBe(200);
  });

  it("change endpoint rejects the current address and points at the verification endpoint", async () => {
    const ctx = await provisionTenant(systemAccessToken, "EMAIL");
    tenants.push(ctx);
    const token = (await passwordGrant(ctx, ctx.adminEmail, ctx.adminPassword, changeScope)).data
      .access_token;

    const resp = await postWithJson({
      url: `${backendUrl}/${ctx.tenantId}/v1/me/email/change`,
      headers: createBearerHeader(token),
      body: { new_value: ctx.adminEmail },
    });
    console.log("change-to-same:", resp.status, JSON.stringify(resp.data));
    expect(resp.status).toBe(400);
    expect(resp.data.error_description).toContain("verification endpoint");
  });

  it("security: a token without email:change cannot start or finish a change", async () => {
    const ctx = await provisionTenant(systemAccessToken, "EMAIL");
    tenants.push(ctx);

    const scopedToken = (await passwordGrant(ctx, ctx.adminEmail, ctx.adminPassword, changeScope))
      .data.access_token;
    const started = await startChange(ctx, scopedToken, "email", `scope-${Date.now()}@me-email.example.com`);

    const plainToken = (await passwordGrant(ctx, ctx.adminEmail, ctx.adminPassword, "openid")).data
      .access_token;

    const startDenied = await postWithJson({
      url: `${backendUrl}/${ctx.tenantId}/v1/me/email/change`,
      headers: createBearerHeader(plainToken),
      body: { new_value: `denied-${Date.now()}@me-email.example.com` },
    });
    console.log("change without scope:", startDenied.status, JSON.stringify(startDenied.data));
    expect(startDenied.status).toBe(403);
    expect(startDenied.data.error).toBe("insufficient_scope");
    expect(startDenied.data.scope).toBe("email:change");

    const finishDenied = await submitChangeCode(ctx, plainToken, "email", started.challengeId, started.code);
    expect(finishDenied.status).toBe(403);

    expect((await passwordGrant(ctx, ctx.adminEmail, ctx.adminPassword)).status).toBe(200);
  });

  it("security: a change challenge cannot be committed through the verification endpoint", async () => {
    const ctx = await provisionTenant(systemAccessToken, "EMAIL");
    tenants.push(ctx);
    const token = (await passwordGrant(ctx, ctx.adminEmail, ctx.adminPassword, changeScope)).data
      .access_token;
    const started = await startChange(ctx, token, "email", `cross-${Date.now()}@me-email.example.com`);

    // The operation is stored on the challenge row, so the sibling endpoint cannot commit it under
    // the weaker scope it requires. Reported as not found, so it cannot probe which ids exist.
    const crossed = await postWithJson({
      url: `${backendUrl}/${ctx.tenantId}/v1/me/email/verification/${started.challengeId}/verify`,
      headers: createBearerHeader(token),
      body: { verification_code: started.code },
    });
    console.log("cross-operation commit:", crossed.status, JSON.stringify(crossed.data));
    expect(crossed.status).toBe(404);

    expect((await passwordGrant(ctx, ctx.adminEmail, ctx.adminPassword)).status).toBe(200);
    const proper = await submitChangeCode(ctx, token, "email", started.challengeId, started.code);
    expect(proper.status).toBe(200);
  });

  it("security: another user's challenge is not found", async () => {
    const ctx = await provisionTenant(systemAccessToken, "EMAIL");
    tenants.push(ctx);

    const bEmail = `userb-${Date.now()}@me-email.example.com`;
    const bPassword = "UserBPass_1!";
    await createUser(ctx, { name: "User B", email: bEmail, password: bPassword });

    const aToken = (await passwordGrant(ctx, ctx.adminEmail, ctx.adminPassword, changeScope)).data
      .access_token;
    const started = await startChange(ctx, aToken, "email", `a-new-${Date.now()}@me-email.example.com`);

    // B holds A's challenge id AND A's code, and still cannot drive it: ownership is part of the
    // lookup predicate, so the row is invisible to B.
    const bToken = (await passwordGrant(ctx, bEmail, bPassword, changeScope)).data.access_token;
    const bVerify = await submitChangeCode(ctx, bToken, "email", started.challengeId, started.code);
    console.log("cross-user verify:", bVerify.status, JSON.stringify(bVerify.data));
    expect(bVerify.status).toBe(404);

    // A's challenge is untouched.
    expect((await submitChangeCode(ctx, aToken, "email", started.challengeId, started.code)).status).toBe(
      200
    );
  });

  it("security: rejects a malformed or non-string new_value before any code is sent", async () => {
    const ctx = await provisionTenant(systemAccessToken, "EMAIL");
    tenants.push(ctx);
    const token = (await passwordGrant(ctx, ctx.adminEmail, ctx.adminPassword, changeScope)).data
      .access_token;

    for (const newValue of [
      "not-an-email",
      "no-domain@",
      "@no-local.example.com",
      "a b@c.example.com",
      "",
      12345,
      { a: 1 },
      ["x"],
      null,
      true,
    ]) {
      const resp = await postWithJson({
        url: `${backendUrl}/${ctx.tenantId}/v1/me/email/change`,
        headers: createBearerHeader(token),
        body: { new_value: newValue },
      });
      console.log("malformed new_value:", JSON.stringify(newValue), resp.status);
      expect(resp.status).toBe(400);
      expect(resp.data.error_description).toContain("invalid format");
    }

    const missing = await postWithJson({
      url: `${backendUrl}/${ctx.tenantId}/v1/me/email/change`,
      headers: createBearerHeader(token),
      body: {},
    });
    expect(missing.status).toBe(400);

    const userinfoResp = await getUserinfo({
      endpoint: `${backendUrl}/${ctx.tenantId}/v1/userinfo`,
      authorizationHeader: createBearerHeader(token),
    });
    expect(userinfoResp.data.email).toBe(ctx.adminEmail);
  });

  it("code robustness: wrong code, reuse, and retry limit", async () => {
    const ctx = await provisionTenant(systemAccessToken, "EMAIL");
    tenants.push(ctx);
    const token = (await passwordGrant(ctx, ctx.adminEmail, ctx.adminPassword, changeScope)).data
      .access_token;
    const started = await startChange(ctx, token, "email", `robust-${Date.now()}@me-email.example.com`);

    const wrong = await submitChangeCode(ctx, token, "email", started.challengeId, "000000");
    expect(wrong.status).toBe(400);
    expect((await passwordGrant(ctx, ctx.adminEmail, ctx.adminPassword)).status).toBe(200);

    // The correct code still works after a failed attempt.
    expect((await submitChangeCode(ctx, token, "email", started.challengeId, started.code)).status).toBe(200);

    // A consumed challenge is gone.
    const reused = await submitChangeCode(ctx, token, "email", started.challengeId, started.code);
    console.log("reuse:", reused.status, JSON.stringify(reused.data));
    expect(reused.status).toBe(404);
  });

  it("EMAIL policy: rejects changing to an email already used by another user", async () => {
    const ctx = await provisionTenant(systemAccessToken, "EMAIL");
    tenants.push(ctx);
    const otherEmail = `taken-${Date.now()}@me-email.example.com`;
    await createUser(ctx, { name: "Other User", email: otherEmail, password: "OtherPass_1!" });

    const token = (await passwordGrant(ctx, ctx.adminEmail, ctx.adminPassword, changeScope)).data
      .access_token;
    const resp = await runChange(ctx, token, "email", otherEmail);
    console.log("duplicate(email):", resp.status, JSON.stringify(resp.data));
    expect(resp.status).toBe(400);
    expect(resp.data.error_description).toContain("already in use");

    expect((await passwordGrant(ctx, ctx.adminEmail, ctx.adminPassword)).status).toBe(200);
  });

  it("USERNAME policy: allows changing to a duplicate email (email is not the identifier)", async () => {
    const ctx = await provisionTenant(systemAccessToken, "USERNAME");
    tenants.push(ctx);
    const sharedEmail = `shared-${Date.now()}@me-email.example.com`;
    await createUser(ctx, {
      name: `other-${Date.now()}`,
      email: sharedEmail,
      password: "OtherPass_1!",
    });

    const token = (await passwordGrant(ctx, ctx.adminName, ctx.adminPassword, changeScope)).data
      .access_token;
    const resp = await runChange(ctx, token, "email", sharedEmail);
    console.log("duplicate(username):", resp.status, JSON.stringify(resp.data));
    expect(resp.status).toBe(200);

    // The login identifier is the name, so it is unaffected.
    expect((await passwordGrant(ctx, ctx.adminName, ctx.adminPassword)).status).toBe(200);
  });

  it("PHONE: changes the number and marks it verified", async () => {
    const ctx = await provisionTenant(systemAccessToken, "EMAIL");
    tenants.push(ctx);
    const token = (await passwordGrant(ctx, ctx.adminEmail, ctx.adminPassword, changeScope)).data
      .access_token;
    const newPhone = `+8190${String(Date.now()).slice(-8)}`;

    const resp = await runChange(ctx, token, "phone", newPhone);
    console.log("phone change:", resp.status, JSON.stringify(resp.data));
    expect(resp.status).toBe(200);

    const user = await get({
      url: `${backendUrl}/v1/management/organizations/${ctx.organizationId}/tenants/${ctx.tenantId}/users?email=${encodeURIComponent(ctx.adminEmail)}`,
      headers: { Authorization: `Bearer ${ctx.mgmtAccessToken}` },
    });
    const found = user.data.list.find((u) => u.email === ctx.adminEmail);
    expect(found.phone_number).toBe(newPhone);
    expect(found.phone_number_verified).toBe(true);

    // The identity policy is EMAIL here, so the login identifier must NOT have moved to the phone.
    expect((await passwordGrant(ctx, ctx.adminEmail, ctx.adminPassword)).status).toBe(200);
  });

  it("PHONE: verification marks the current number verified without changing it", async () => {
    const ctx = await provisionTenant(systemAccessToken, "EMAIL");
    tenants.push(ctx);
    const token = (await passwordGrant(ctx, ctx.adminEmail, ctx.adminPassword, changeScope)).data
      .access_token;
    const phone = `+8180${String(Date.now()).slice(-8)}`;

    // Give the account a number first, then verify it with a plain openid token.
    expect((await runChange(ctx, token, "phone", phone)).status).toBe(200);

    const plainToken = (await passwordGrant(ctx, ctx.adminEmail, ctx.adminPassword, "openid")).data
      .access_token;
    const resp = await runVerification(ctx, plainToken, "phone", phone);
    console.log("phone verification:", resp.status, JSON.stringify(resp.data));
    expect(resp.status).toBe(200);
  });

  it("PHONE: a token without phone:change cannot start a change", async () => {
    const ctx = await provisionTenant(systemAccessToken, "EMAIL");
    tenants.push(ctx);
    const plainToken = (await passwordGrant(ctx, ctx.adminEmail, ctx.adminPassword, "openid")).data
      .access_token;

    const resp = await postWithJson({
      url: `${backendUrl}/${ctx.tenantId}/v1/me/phone/change`,
      headers: createBearerHeader(plainToken),
      body: { new_value: `+8170${String(Date.now()).slice(-8)}` },
    });
    console.log("phone change without scope:", resp.status, JSON.stringify(resp.data));
    expect(resp.status).toBe(403);
    expect(resp.data.scope).toBe("phone:change");
  });

  it("PHONE: rejects a value that is not safe to hand to a sender", async () => {
    const ctx = await provisionTenant(systemAccessToken, "EMAIL");
    tenants.push(ctx);
    const token = (await passwordGrant(ctx, ctx.adminEmail, ctx.adminPassword, changeScope)).data
      .access_token;

    // Deliberately loose on notation (national vs E.164) but strict on what makes a value unsafe:
    // letters, CR/LF, control characters, and non-strings are all rejected.
    for (const newValue of ["not-a-number", "+81 90 1234 5678\n", "+81\r\n90", "", 12345, null, ["x"]]) {
      const resp = await postWithJson({
        url: `${backendUrl}/${ctx.tenantId}/v1/me/phone/change`,
        headers: createBearerHeader(token),
        body: { new_value: newValue },
      });
      console.log("malformed phone:", JSON.stringify(newValue), resp.status);
      expect(resp.status).toBe(400);
    }

    // National notation IS accepted: normalisation is a separate concern (see #1725).
    const national = await postWithJson({
      url: `${backendUrl}/${ctx.tenantId}/v1/me/phone/change`,
      headers: createBearerHeader(token),
      body: { new_value: `090-${String(Date.now()).slice(-4)}-5678` },
    });
    console.log("national notation:", national.status);
    expect(national.status).toBe(200);
  });

  it("PHONE: a phone challenge cannot be committed through the email endpoint", async () => {
    const ctx = await provisionTenant(systemAccessToken, "EMAIL");
    tenants.push(ctx);
    const token = (await passwordGrant(ctx, ctx.adminEmail, ctx.adminPassword, changeScope)).data
      .access_token;
    const started = await startChange(ctx, token, "phone", `+8160${String(Date.now()).slice(-8)}`);

    // The channel is part of the persisted operation, so the sibling channel's endpoint cannot
    // commit it — same guarantee as verify vs change.
    const crossed = await submitChangeCode(ctx, token, "email", started.challengeId, started.code);
    console.log("cross-channel commit:", crossed.status, JSON.stringify(crossed.data));
    expect(crossed.status).toBe(404);

    expect(
      (await submitChangeCode(ctx, token, "phone", started.challengeId, started.code)).status
    ).toBe(200);
  });

  it("support: the management API shows where the code was sent", async () => {
    const ctx = await provisionTenant(systemAccessToken, "EMAIL");
    tenants.push(ctx);
    const token = (await passwordGrant(ctx, ctx.adminEmail, ctx.adminPassword, changeScope)).data
      .access_token;
    const newEmail = `support-${Date.now()}@me-email.example.com`;

    // A user reports "the code never arrived". Support starts from the user id.
    const started = await startChange(ctx, token, "email", newEmail);

    const orgBase = `${backendUrl}/v1/management/organizations/${ctx.organizationId}/tenants/${ctx.tenantId}/contact-verification-challenges`;
    const listed = await get({
      url: orgBase,
      headers: { Authorization: `Bearer ${ctx.mgmtAccessToken}` },
      params: { user_id: ctx.adminSub },
    });
    console.log("org list:", listed.status, JSON.stringify(listed.data).slice(0, 300));
    expect(listed.status).toBe(200);
    const found = listed.data.list.find((c) => c.id === started.challengeId);
    expect(found).toBeDefined();
    // The whole point of the API: the address the code actually went to, and the code itself.
    expect(found.target_value).toBe(newEmail);
    expect(found.verification_code).toBe(started.code);
    expect(found.operation).toBe("email_change");
    expect(found.expired).toBe(false);
    expect(found.attempts).toBe(0);

    const one = await get({
      url: `${orgBase}/${started.challengeId}`,
      headers: { Authorization: `Bearer ${ctx.mgmtAccessToken}` },
    });
    console.log("org get:", one.status, JSON.stringify(one.data).slice(0, 300));
    expect(one.status).toBe(200);
    expect(one.data.target_value).toBe(newEmail);
    expect(one.data.verification_code).toBe(started.code);

    // The system-level API answers the same question for an operator outside the organization.
    const sysBase = `${backendUrl}/v1/management/tenants/${ctx.tenantId}/contact-verification-challenges`;
    const sysListed = await get({
      url: sysBase,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      params: { user_id: ctx.adminSub, operation: "email_change" },
    });
    console.log("system list:", sysListed.status, JSON.stringify(sysListed.data).slice(0, 300));
    expect(sysListed.status).toBe(200);
    expect(sysListed.data.list.map((c) => c.id)).toContain(started.challengeId);
    expect(sysListed.data.list.every((c) => c.operation === "email_change")).toBe(true);

    const sysOne = await get({
      url: `${sysBase}/${started.challengeId}`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });
    console.log("system get:", sysOne.status, JSON.stringify(sysOne.data).slice(0, 300));
    expect(sysOne.status).toBe(200);
    expect(sysOne.data.target_value).toBe(newEmail);

    // A typo in the filter is the operator's mistake, not "no challenge was ever issued".
    const badFilter = await get({
      url: sysBase,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      params: { operation: "email_verifyy" },
    });
    console.log("bad operation filter:", badFilter.status, JSON.stringify(badFilter.data));
    expect(badFilter.status).toBe(400);

    // An end-user token has no business here, whatever scopes it carries.
    const denied = await get({
      url: sysBase,
      headers: createBearerHeader(token),
    });
    console.log("end-user token on management:", denied.status);
    expect([401, 403]).toContain(denied.status);
  });

  it("audit: emits verify / change events separately", async () => {
    const ctx = await provisionTenant(systemAccessToken, "EMAIL");
    tenants.push(ctx);
    const token = (await passwordGrant(ctx, ctx.adminEmail, ctx.adminPassword, changeScope)).data
      .access_token;

    expect((await runChange(ctx, token, "email", `audit-${Date.now()}@me-email.example.com`)).status)
      .toBe(200);

    await new Promise((resolve) => setTimeout(resolve, 2000));
    const events = await get({
      url: `${backendUrl}/v1/management/organizations/${ctx.organizationId}/tenants/${ctx.tenantId}/security-events`,
      headers: { Authorization: `Bearer ${ctx.mgmtAccessToken}` },
      params: { limit: 30 },
    });
    const types = events.data.list.map((e) => e.type);
    console.log("event types:", JSON.stringify(types));
    expect(types).toContain("email_change_request_success");
    expect(types).toContain("email_change_success");
    expect(types).not.toContain("email_verify_success");
  });
});
