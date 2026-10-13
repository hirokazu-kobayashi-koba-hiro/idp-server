import { describe, expect, it, beforeAll } from "@jest/globals";
import { get, postWithJson } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { onboarding } from "../../../api/managementClient";
import { generateRS256KeyPair } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import { faker } from "@faker-js/faker";
import { generateValidCredentialFromChallenge } from "../../../lib/fido/fido2";

/**
 * FIDO2 deregistration deletes only the authenticated user's own credential.
 *
 * - The user's own credential is deleted (200)
 * - A credential that belongs to another user is not deleted, and is reported the same as a
 *   missing one (400 credential_not_found)
 */
describe("FIDO2 deregistration - credential ownership", () => {
  const redirectUri = "https://app.example.com/callback";
  let tenantId;
  let clientId;

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

    const timestamp = Date.now();
    const organizationId = uuidv4();
    tenantId = uuidv4();
    clientId = uuidv4();
    const { jwks } = await generateRS256KeyPair();

    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `FIDO2 Deregistration Org ${timestamp}`,
        description: "Test organization for FIDO2 deregistration",
      },
      tenant: {
        id: tenantId,
        name: `FIDO2 Deregistration Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
      },
      authorization_server: {
        issuer: `${backendUrl}/${tenantId}`,
        authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
        token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        token_endpoint_auth_methods_supported: ["client_secret_post", "client_secret_basic"],
        token_endpoint_auth_signing_alg_values_supported: ["RS256", "ES256"],
        userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
        jwks: jwks,
        grant_types_supported: ["authorization_code", "refresh_token", "password"],
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        scopes_supported: ["openid", "profile", "email", "management"],
        response_types_supported: ["code"],
        response_modes_supported: ["query", "fragment"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["RS256", "ES256"],
        claims_parameter_supported: true,
        extension: {
          access_token_type: "JWT",
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          access_token_duration: 3600,
          id_token_duration: 3600,
          refresh_token_duration: 86400,
        },
      },
      user: {
        sub: uuidv4(),
        provider_id: "idp-server",
        name: faker.internet.email(),
        email: faker.internet.email(),
        email_verified: true,
        raw_password: `AdminPass${timestamp}!`,
      },
      client: {
        client_id: uuidv4(),
        client_id_alias: `admin-client-dereg-${timestamp}`,
        client_secret: `admin-secret-${timestamp}`,
        redirect_uris: [redirectUri],
        response_types: ["code"],
        grant_types: ["authorization_code", "refresh_token", "password"],
        scope: "openid profile email management",
        client_name: "Admin Client",
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
      },
    };

    const onboardingResponse = await onboarding({
      headers: { Authorization: `Bearer ${systemTokenResponse.data.access_token}` },
      body: onboardingRequest,
    });
    expect(onboardingResponse.status).toBe(201);

    const createdClient = onboardingResponse.data.client;
    const adminTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: onboardingRequest.user.email,
      password: onboardingRequest.user.raw_password,
      scope: "management",
      clientId: createdClient.client_id,
      clientSecret: createdClient.client_secret,
    });
    expect(adminTokenResponse.status).toBe(200);
    const headers = { Authorization: `Bearer ${adminTokenResponse.data.access_token}` };
    const managementBase = `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`;

    const emailConfigResponse = await postWithJson({
      url: `${managementBase}/authentication-configurations`,
      headers,
      body: {
        id: uuidv4(),
        type: "email",
        attributes: {},
        metadata: {
          type: "external",
          sender: "test@gmail.com",
          templates: { authentication: { subject: "Code", body: "{VERIFICATION_CODE}" } },
          settings: { smtp: { host: "smtp.gmail.com", port: 587, username: "test", password: "test", auth: true, starttls: { enable: true } } },
          expire_seconds: 300,
        },
        interactions: {
          "email-authentication-challenge": {
            execution: {
              function: "http_request",
              http_request: {
                url: "http://host.docker.internal:4000/email-authentication-challenge",
                method: "POST",
                header_mapping_rules: [{ static_value: "application/json", to: "Content-Type" }],
                body_mapping_rules: [{ from: "$.request_body", to: "*" }],
              },
              http_request_store: {
                key: "email-authentication-challenge",
                interaction_mapping_rules: [{ from: "$.response_body.transaction_id", to: "transaction_id" }],
              },
            },
            response: { body_mapping_rules: [{ from: "$.execution_http_request.response_body", to: "*" }] },
          },
          "email-authentication": {
            execution: {
              function: "http_request",
              previous_interaction: { key: "email-authentication-challenge" },
              http_request: {
                url: "http://host.docker.internal:4000/email-authentication",
                method: "POST",
                header_mapping_rules: [{ static_value: "application/json", to: "Content-Type" }],
                body_mapping_rules: [{ from: "$.request_body", to: "*" }],
              },
            },
            response: { body_mapping_rules: [{ from: "$.execution_http_request.response_body", to: "*" }] },
          },
        },
      },
    });
    expect(emailConfigResponse.status).toBe(201);

    const fido2Details = {
      rp_id: "localhost",
      origin: "http://localhost:3000",
      rp_name: "FIDO2 Deregistration Test",
      user_verification_required: true,
      user_presence_required: true,
      require_resident_key: true,
    };
    const fido2Response = { body_mapping_rules: [{ from: "$.execution_webauthn4j", to: "*" }] };
    const fido2ConfigResponse = await postWithJson({
      url: `${managementBase}/authentication-configurations`,
      headers,
      body: {
        id: uuidv4(),
        type: "fido2",
        attributes: {},
        metadata: {},
        interactions: {
          "fido2-registration-challenge": {
            execution: { function: "webauthn4j_registration_challenge", details: fido2Details },
            response: fido2Response,
          },
          "fido2-registration": {
            execution: { function: "webauthn4j_registration", details: fido2Details },
            response: fido2Response,
          },
          "fido2-deregistration": {
            execution: { function: "webauthn4j_deregistration", details: fido2Details },
            response: {
              body_mapping_rules: [
                { from: "$.execution_webauthn4j", to: "*" },
                { from: "$.error", to: "error", condition: { operation: "exists", path: "$.error" } },
                { from: "$.error_description", to: "error_description", condition: { operation: "exists", path: "$.error_description" } },
              ],
            },
          },
        },
      },
    });
    expect(fido2ConfigResponse.status).toBe(201);

    const policyResponse = await postWithJson({
      url: `${managementBase}/authentication-policies`,
      headers,
      body: {
        id: uuidv4(),
        flow: "oauth",
        enabled: true,
        policies: [{
          priority: 10,
          conditions: { scopes: ["openid"] },
          available_methods: ["password", "email", "fido2", "initial-registration"],
          success_conditions: {
            any_of: [
              [{ path: "$.email-authentication.success_count", type: "integer", operation: "gte", value: 1 }],
            ],
          },
          device_registration_conditions: {
            any_of: [
              [{ path: "$.email-authentication.success_count", type: "integer", operation: "gte", value: 1 }],
            ],
          },
        }],
      },
    });
    expect(policyResponse.status).toBe(201);

    const clientResponse = await postWithJson({
      url: `${managementBase}/clients`,
      headers,
      body: {
        client_id: clientId,
        client_secret: `client-secret-${timestamp}`,
        client_name: "FIDO2 Deregistration Test Client",
        redirect_uris: [redirectUri],
        grant_types: ["authorization_code"],
        response_types: ["code"],
        scope: "openid profile email",
        token_endpoint_auth_method: "client_secret_post",
      },
    });
    expect(clientResponse.status).toBe(201);
  });

  const authorizationsUrl = (authId, interaction) =>
    `${backendUrl}/${tenantId}/v1/authorizations/${authId}/${interaction}`;

  const startAuthorization = async () => {
    const params = new URLSearchParams({
      response_type: "code",
      client_id: clientId,
      redirect_uri: redirectUri,
      scope: "openid profile email",
      state: `state_${Date.now()}`,
    });
    const response = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${params.toString()}`,
      headers: {},
    });
    expect(response.status).toBe(302);
    return new URL(response.headers.location, backendUrl).searchParams.get("id");
  };

  const authenticateWithEmail = async (authId, email) => {
    const challenge = await postWithJson({
      url: authorizationsUrl(authId, "email-authentication-challenge"),
      body: { email, provider_id: "idp-server" },
    });
    expect(challenge.status).toBe(200);
    const verification = await postWithJson({
      url: authorizationsUrl(authId, "email-authentication"),
      body: { code: challenge.data.verification_code || "123456" },
    });
    expect(verification.status).toBe(200);
  };

  /** Creates a user who has signed in with email and registered a passkey. */
  const registerUserWithPasskey = async () => {
    const email = faker.internet.email();
    const name = faker.person.fullName();
    const authId = await startAuthorization();

    const registration = await postWithJson({
      url: authorizationsUrl(authId, "initial-registration"),
      body: { email, password: `Password${Date.now()}!`, name },
    });
    expect(registration.status).toBe(200);
    await authenticateWithEmail(authId, email);

    const challenge = await postWithJson({
      url: authorizationsUrl(authId, "fido2-registration-challenge"),
      body: { username: email, displayName: name },
    });
    expect(challenge.status).toBe(200);
    const credential = generateValidCredentialFromChallenge(challenge.data);
    const registered = await postWithJson({
      url: authorizationsUrl(authId, "fido2-registration"),
      body: credential,
    });
    expect(registered.status).toBe(200);

    const authorized = await postWithJson({ url: authorizationsUrl(authId, "authorize"), body: {} });
    expect(authorized.status).toBe(200);

    return { email, credentialId: credential.id };
  };

  /** Starts a new authorization and signs in as the user with email. */
  const signInWithEmail = async (email) => {
    const authId = await startAuthorization();
    await authenticateWithEmail(authId, email);
    return authId;
  };

  it("deletes the user's own credential", async () => {
    const user = await registerUserWithPasskey();
    const authId = await signInWithEmail(user.email);

    const response = await postWithJson({
      url: authorizationsUrl(authId, "fido2-deregistration"),
      body: { credential_id: user.credentialId },
    });
    console.log("own credential:", response.status, response.data);

    expect(response.status).toBe(200);
    expect(response.data.credential_id).toBe(user.credentialId);
    expect(response.data.deleted).toBe(true);
  });

  it("does not delete a credential that belongs to another user", async () => {
    const owner = await registerUserWithPasskey();
    const other = await registerUserWithPasskey();
    const authId = await signInWithEmail(other.email);

    const response = await postWithJson({
      url: authorizationsUrl(authId, "fido2-deregistration"),
      body: { credential_id: owner.credentialId },
    });
    console.log("another user's credential:", response.status, response.data);

    // Reported the same as a missing credential, so its existence is not revealed
    expect(response.status).toBe(400);
    expect(response.data.error).toBe("credential_not_found");

    // The owner can still delete it, so it was not removed
    const ownerAuthId = await signInWithEmail(owner.email);
    const ownerResponse = await postWithJson({
      url: authorizationsUrl(ownerAuthId, "fido2-deregistration"),
      body: { credential_id: owner.credentialId },
    });
    expect(ownerResponse.status).toBe(200);
  });

  it("ignores a username in the request body and checks against the signed-in user", async () => {
    const owner = await registerUserWithPasskey();
    const other = await registerUserWithPasskey();
    const authId = await signInWithEmail(other.email);

    const response = await postWithJson({
      url: authorizationsUrl(authId, "fido2-deregistration"),
      body: { credential_id: owner.credentialId, username: owner.email },
    });
    console.log("username in body:", response.status, response.data);

    expect(response.status).toBe(400);
    expect(response.data.error).toBe("credential_not_found");

    // The owner can still delete it, so it was not removed
    const ownerAuthId = await signInWithEmail(owner.email);
    const ownerResponse = await postWithJson({
      url: authorizationsUrl(ownerAuthId, "fido2-deregistration"),
      body: { credential_id: owner.credentialId },
    });
    expect(ownerResponse.status).toBe(200);
  });
});
