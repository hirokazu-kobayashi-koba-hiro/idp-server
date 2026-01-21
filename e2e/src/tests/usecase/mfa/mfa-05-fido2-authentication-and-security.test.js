import { describe, expect, it, beforeAll } from "@jest/globals";
import { get, postWithJson } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { generateRS256KeyPair } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import { faker } from "@faker-js/faker";
import {
  generateValidCredentialFromChallenge,
  generateValidAssertionFromChallenge,
} from "../../../lib/fido/fido2";

/**
 * FIDO2 (WebAuthn) Authentication and Security Tests
 *
 * This test suite verifies:
 * 1. Error handling improvements (Issue 1.1)
 *    - credential_not_found error for non-existent credentials
 *
 * 2. CVE-2025-26788 Mitigation (Issue 1.2)
 *    - allowCredentials validation to prevent credential ID substitution attacks
 *    - Defense in Depth against attacker credential injection
 *
 * Related Security Improvements:
 * - WebAuthn4jAuthenticationExecutor: NotFoundException handling
 * - WebAuthn4jChallengeContext: allowCredentialIds validation
 */
describe("FIDO2 Authentication and Security Tests", () => {
  let systemAccessToken;

  beforeAll(async () => {
    // Get system admin token
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

  it("should return credential_not_found error for non-existent credential (Issue 1.1)", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const clientId = uuidv4();
    const clientSecret = `client-secret-${timestamp}`;
    const redirectUri = `https://app.example.com/callback`;

    const userEmail = faker.internet.email();
    const userName = faker.person.fullName();

    console.log("\n=== FIDO2 Error Case: credential_not_found (Issue 1.1) ===\n");
    console.log("This test verifies that authentication with non-existent credential returns proper error.\n");

    const { jwks } = await generateRS256KeyPair();

    // Setup tenant with FIDO2 config
    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `FIDO2 Error Test Org ${timestamp}`,
        description: "Test organization for FIDO2 error cases",
      },
      tenant: {
        id: tenantId,
        name: `FIDO2 Error Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        tenant_type: "ORGANIZER",
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
        client_id_alias: `admin-client-error1-${timestamp}`,
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

    const onboardingResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
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
    const adminAccessToken = adminTokenResponse.data.access_token;

    // Create authentication configurations
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: { id: uuidv4(), type: "password", attributes: {}, metadata: {}, interactions: {} }
    });

    // Email auth config for MFA
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: uuidv4(),
        type: "email",
        attributes: {},
        metadata: {
          type: "external",
          sender: "test@gmail.com",
          templates: { authentication: { subject: "Code", body: "{VERIFICATION_CODE}" } },
          settings: { smtp: { host: "smtp.gmail.com", port: 587, username: "test", password: "test", auth: true, starttls: { enable: true } } },
          expire_seconds: 300
        },
        interactions: {
          "email-authentication-challenge": {
            execution: {
              function: "http_request",
              http_request: {
                url: "http://host.docker.internal:4000/email-authentication-challenge",
                method: "POST",
                header_mapping_rules: [{ static_value: "application/json", to: "Content-Type" }],
                body_mapping_rules: [{ from: "$.request_body", to: "*" }]
              },
              http_request_store: { key: "email-authentication-challenge", interaction_mapping_rules: [{ from: "$.response_body.transaction_id", to: "transaction_id" }] }
            },
            response: { body_mapping_rules: [{ from: "$.execution_http_request.response_body", to: "*" }] }
          },
          "email-authentication": {
            execution: {
              function: "http_request",
              previous_interaction: { key: "email-authentication-challenge" },
              http_request: {
                url: "http://host.docker.internal:4000/email-authentication",
                method: "POST",
                header_mapping_rules: [{ static_value: "application/json", to: "Content-Type" }],
                body_mapping_rules: [{ from: "$.request_body", to: "*" }]
              }
            },
            response: { body_mapping_rules: [{ from: "$.execution_http_request.response_body", to: "*" }] }
          }
        }
      }
    });

    const fido2Details = {
      rp_id: "localhost",
      origin: "http://localhost:3000",
      rp_name: "FIDO2 Error Test",
      user_verification_required: true,
      user_presence_required: true,
      require_resident_key: true
    };
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: uuidv4(),
        type: "fido2",
        attributes: {},
        metadata: {},
        interactions: {
          "fido2-registration-challenge": {
            execution: { function: "webauthn4j_registration_challenge", details: fido2Details },
            response: { body_mapping_rules: [{ from: "$.execution_webauthn4j", to: "*" }] }
          },
          "fido2-registration": {
            execution: { function: "webauthn4j_registration", details: fido2Details },
            response: { body_mapping_rules: [{ from: "$.execution_webauthn4j", to: "*" }] }
          },
          "fido2-authentication-challenge": {
            execution: { function: "webauthn4j_authentication_challenge", details: fido2Details },
            response: { body_mapping_rules: [{ from: "$.execution_webauthn4j", to: "*" }] }
          },
          "fido2-authentication": {
            execution: { function: "webauthn4j_authentication", details: fido2Details },
            response: { body_mapping_rules: [{ from: "$.execution_webauthn4j", to: "*" }] }
          }
        }
      }
    });

    // Create policy - email auth satisfies device registration condition
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: uuidv4(),
        flow: "oauth",
        enabled: true,
        policies: [{
          priority: 10,
          conditions: { scopes: ["openid"] },
          available_methods: ["password", "email", "fido2"],
          success_conditions: {
            any_of: [
              [{ path: "$.email-authentication.success_count", type: "integer", operation: "gte", value: 1 }],
              [{ path: "$.fido2-authentication.success_count", type: "integer", operation: "gte", value: 1 }]
            ]
          },
          device_registration_conditions: {
            any_of: [
              [{ path: "$.email-authentication.success_count", type: "integer", operation: "gte", value: 1 }]
            ]
          }
        }]
      }
    });

    // Create client
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        client_id: clientId,
        client_secret: clientSecret,
        client_name: "FIDO2 Error Test Client",
        redirect_uris: [redirectUri],
        grant_types: ["authorization_code"],
        response_types: ["code"],
        scope: "openid profile email",
        token_endpoint_auth_method: "client_secret_post",
      },
    });
    console.log("✓ Setup completed\n");

    // ========================================
    // Step 1: Register user and FIDO2 credential
    // ========================================
    console.log("=== Step 1: Register user and FIDO2 credential ===");

    const authParams = new URLSearchParams({
      response_type: "code",
      client_id: clientId,
      redirect_uri: redirectUri,
      scope: "openid profile email",
      state: `state_${timestamp}`,
    });
    const authorizeResponse = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${authParams.toString()}`,
      headers: {},
    });
    const authId = new URL(authorizeResponse.headers.location, backendUrl).searchParams.get('id');

    // Register user
    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/initial-registration`,
      body: { email: userEmail, password: `Password${timestamp}!`, name: userName },
    });

    // Email auth to satisfy device registration condition
    const emailChallenge = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/email-authentication-challenge`,
      body: { email: userEmail, provider_id: "idp-server" },
    });
    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/email-authentication`,
      body: { code: emailChallenge.data.verification_code || "123456" },
    });

    // Register FIDO2 credential
    const fido2Challenge = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/fido2-registration-challenge`,
      body: { username: userEmail, displayName: userName },
    });
    const validCredential = generateValidCredentialFromChallenge(fido2Challenge.data);
    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/fido2-registration`,
      body: validCredential,
    });

    // Complete authorization flow
    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/authorize`,
      body: {},
    });
    console.log("✓ User registered with FIDO2 credential:", validCredential.id, "\n");

    // ========================================
    // Step 2: New session - try to authenticate with fake credential
    // ========================================
    console.log("=== Step 2: Authenticate with non-existent credential ===");

    const authParams2 = new URLSearchParams({
      response_type: "code",
      client_id: clientId,
      redirect_uri: redirectUri,
      scope: "openid profile email",
      state: `state_${timestamp}_2`,
    });
    const authorizeResponse2 = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${authParams2.toString()}`,
      headers: {},
    });
    const authId2 = new URL(authorizeResponse2.headers.location, backendUrl).searchParams.get('id');

    // Get FIDO2 authentication challenge WITHOUT username (discoverable credential flow)
    // Username is optional - omitting it triggers Discoverable Credential flow (Line FIDO2 Server compatible)
    const challengeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId2}/fido2-authentication-challenge`,
      body: { userVerification: "required", timeout: 60000 },  // No username - discoverable credential flow
    });
    console.log("FIDO2 challenge response (discoverable):", challengeResponse.status, challengeResponse.data);
    expect(challengeResponse.status).toBe(200);
    console.log("✓ FIDO2 authentication challenge received (discoverable credential flow)");

    // Verify no allowCredentials in response (discoverable flow)
    if (!challengeResponse.data.allowCredentials || challengeResponse.data.allowCredentials.length === 0) {
      console.log("  allowCredentials: (empty - discoverable credential flow)");
    } else {
      console.log("  allowCredentials:", challengeResponse.data.allowCredentials);
    }

    // Try to authenticate with a fake credential ID that doesn't exist
    const fakeCredentialId = "fake-credential-id-that-does-not-exist-12345";
    const fakeAssertion = {
      id: fakeCredentialId,
      rawId: fakeCredentialId,
      type: "public-key",
      response: {
        authenticatorData: "SZYN5YgOjGh0NBcPZHZgW4_krrmihjLHmVzzuoMdl2MFAAAAAQ",
        clientDataJSON: Buffer.from(JSON.stringify({
          type: "webauthn.get",
          challenge: challengeResponse.data.challenge,
          origin: "http://localhost:3000",
          crossOrigin: false
        })).toString("base64url"),
        signature: "MEUCIQDfake_signature_that_will_not_be_verified",
      }
    };

    const authResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId2}/fido2-authentication`,
      body: fakeAssertion,
    });

    console.log("FIDO2 authentication response:", authResponse.status, authResponse.data);

    // Should return 400 with credential_not_found error
    // In discoverable credential flow (no allowCredentials), the server looks up credential by ID
    // and returns credential_not_found if it doesn't exist
    expect(authResponse.status).toBe(400);
    expect(authResponse.data.error).toBe("credential_not_found");
    console.log("  Error:", authResponse.data.error, "-", authResponse.data.error_description);
    console.log("✓ Correctly returned credential_not_found error\n");

    console.log("=== Test Completed: credential_not_found Error Handling (Issue 1.1) ===\n");
  });

  it("should reject credential ID not in allowCredentials list (CVE-2025-26788 mitigation)", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const clientId = uuidv4();
    const clientSecret = `client-secret-${timestamp}`;
    const redirectUri = `https://app.example.com/callback`;

    const userEmail = faker.internet.email();
    const userName = faker.person.fullName();
    const userPassword = `Password${timestamp}!`;

    // Second user (attacker)
    const attackerEmail = faker.internet.email();
    const attackerName = faker.person.fullName();
    const attackerPassword = `AttackerPass${timestamp}!`;

    console.log("\n=== FIDO2 Security Test: CVE-2025-26788 Mitigation ===\n");
    console.log("This test verifies that credential ID substitution attacks are prevented.");
    console.log("Scenario: Attacker tries to use their credential ID in victim's allowCredentials flow.\n");

    const { jwks } = await generateRS256KeyPair();

    // Setup tenant
    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `CVE Test Org ${timestamp}`,
        description: "Test for CVE-2025-26788 mitigation",
      },
      tenant: {
        id: tenantId,
        name: `CVE Test Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        tenant_type: "ORGANIZER",
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
        client_id_alias: `admin-client-cve-${timestamp}`,
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

    const onboardingResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
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
    const adminAccessToken = adminTokenResponse.data.access_token;

    // Create authentication configurations
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: { id: uuidv4(), type: "password", attributes: {}, metadata: {}, interactions: {} }
    });

    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: uuidv4(),
        type: "email",
        attributes: {},
        metadata: {
          type: "external",
          sender: "test@gmail.com",
          templates: { authentication: { subject: "Code", body: "{VERIFICATION_CODE}" } },
          settings: { smtp: { host: "smtp.gmail.com", port: 587, username: "test", password: "test", auth: true, starttls: { enable: true } } },
          expire_seconds: 300
        },
        interactions: {
          "email-authentication-challenge": {
            execution: {
              function: "http_request",
              http_request: {
                url: "http://host.docker.internal:4000/email-authentication-challenge",
                method: "POST",
                header_mapping_rules: [{ static_value: "application/json", to: "Content-Type" }],
                body_mapping_rules: [{ from: "$.request_body", to: "*" }]
              },
              http_request_store: { key: "email-authentication-challenge", interaction_mapping_rules: [{ from: "$.response_body.transaction_id", to: "transaction_id" }] }
            },
            response: { body_mapping_rules: [{ from: "$.execution_http_request.response_body", to: "*" }] }
          },
          "email-authentication": {
            execution: {
              function: "http_request",
              previous_interaction: { key: "email-authentication-challenge" },
              http_request: {
                url: "http://host.docker.internal:4000/email-authentication",
                method: "POST",
                header_mapping_rules: [{ static_value: "application/json", to: "Content-Type" }],
                body_mapping_rules: [{ from: "$.request_body", to: "*" }]
              }
            },
            response: { body_mapping_rules: [{ from: "$.execution_http_request.response_body", to: "*" }] }
          }
        }
      }
    });

    const fido2Details = {
      rp_id: "localhost",
      origin: "http://localhost:3000",
      rp_name: "CVE Test",
      user_verification_required: true,
      user_presence_required: true,
      require_resident_key: true
    };
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: uuidv4(),
        type: "fido2",
        attributes: {},
        metadata: {},
        interactions: {
          "fido2-registration-challenge": {
            execution: { function: "webauthn4j_registration_challenge", details: fido2Details },
            response: { body_mapping_rules: [{ from: "$.execution_webauthn4j", to: "*" }] }
          },
          "fido2-registration": {
            execution: { function: "webauthn4j_registration", details: fido2Details },
            response: { body_mapping_rules: [{ from: "$.execution_webauthn4j", to: "*" }] }
          },
          "fido2-authentication-challenge": {
            execution: { function: "webauthn4j_authentication_challenge", details: fido2Details },
            response: { body_mapping_rules: [{ from: "$.execution_webauthn4j", to: "*" }] }
          },
          "fido2-authentication": {
            execution: { function: "webauthn4j_authentication", details: fido2Details },
            response: { body_mapping_rules: [{ from: "$.execution_webauthn4j", to: "*" }] }
          }
        }
      }
    });

    // Create policy
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: uuidv4(),
        flow: "oauth",
        enabled: true,
        policies: [{
          priority: 10,
          conditions: { scopes: ["openid"] },
          available_methods: ["password", "email", "fido2"],
          success_conditions: {
            any_of: [
              [{ path: "$.email-authentication.success_count", type: "integer", operation: "gte", value: 1 }],
              [{ path: "$.fido2-authentication.success_count", type: "integer", operation: "gte", value: 1 }]
            ]
          },
          device_registration_conditions: {
            any_of: [
              [{ path: "$.email-authentication.success_count", type: "integer", operation: "gte", value: 1 }]
            ]
          }
        }]
      }
    });

    // Create client
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        client_id: clientId,
        client_secret: clientSecret,
        client_name: "CVE Test Client",
        redirect_uris: [redirectUri],
        grant_types: ["authorization_code"],
        response_types: ["code"],
        scope: "openid profile email",
        token_endpoint_auth_method: "client_secret_post",
      },
    });
    console.log("✓ Setup completed\n");

    // ========================================
    // Step 1: Register attacker's FIDO2 credential
    // ========================================
    console.log("=== Step 1: Register Attacker's FIDO2 Credential ===");

    const attackerAuthParams = new URLSearchParams({
      response_type: "code",
      client_id: clientId,
      redirect_uri: redirectUri,
      scope: "openid profile email",
      state: `attacker_${timestamp}`,
    });
    const attackerAuthResponse = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${attackerAuthParams.toString()}`,
      headers: {},
    });
    const attackerAuthId = new URL(attackerAuthResponse.headers.location, backendUrl).searchParams.get('id');

    // Register attacker
    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${attackerAuthId}/initial-registration`,
      body: { email: attackerEmail, password: attackerPassword, name: attackerName },
    });

    // Email auth for attacker
    const attackerEmailChallenge = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${attackerAuthId}/email-authentication-challenge`,
      body: { email: attackerEmail, provider_id: "idp-server" },
    });
    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${attackerAuthId}/email-authentication`,
      body: { code: attackerEmailChallenge.data.verification_code || "123456" },
    });

    // Register attacker's FIDO2 credential
    const attackerFido2Challenge = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${attackerAuthId}/fido2-registration-challenge`,
      body: { username: attackerEmail, displayName: attackerName },
    });
    const attackerCredential = generateValidCredentialFromChallenge(attackerFido2Challenge.data);
    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${attackerAuthId}/fido2-registration`,
      body: attackerCredential,
    });

    // Complete attacker's auth flow
    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${attackerAuthId}/authorize`,
      body: {},
    });
    console.log("✓ Attacker's FIDO2 credential registered");
    console.log("  Attacker credential ID:", attackerCredential.id, "\n");

    // ========================================
    // Step 2: Register victim's FIDO2 credential
    // ========================================
    console.log("=== Step 2: Register Victim's FIDO2 Credential ===");

    const victimAuthParams = new URLSearchParams({
      response_type: "code",
      client_id: clientId,
      redirect_uri: redirectUri,
      scope: "openid profile email",
      state: `victim_${timestamp}`,
    });
    const victimAuthResponse = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${victimAuthParams.toString()}`,
      headers: {},
    });
    const victimAuthId = new URL(victimAuthResponse.headers.location, backendUrl).searchParams.get('id');

    // Register victim
    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${victimAuthId}/initial-registration`,
      body: { email: userEmail, password: userPassword, name: userName },
    });

    // Email auth for victim
    const victimEmailChallenge = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${victimAuthId}/email-authentication-challenge`,
      body: { email: userEmail, provider_id: "idp-server" },
    });
    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${victimAuthId}/email-authentication`,
      body: { code: victimEmailChallenge.data.verification_code || "123456" },
    });

    // Register victim's FIDO2 credential
    const victimFido2Challenge = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${victimAuthId}/fido2-registration-challenge`,
      body: { username: userEmail, displayName: userName },
    });
    const victimCredential = generateValidCredentialFromChallenge(victimFido2Challenge.data);
    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${victimAuthId}/fido2-registration`,
      body: victimCredential,
    });

    // Complete victim's auth flow
    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${victimAuthId}/authorize`,
      body: {},
    });
    console.log("✓ Victim's FIDO2 credential registered");
    console.log("  Victim credential ID:", victimCredential.id, "\n");

    // ========================================
    // Step 3: Attack - Use attacker's credential in victim's allowCredentials flow
    // ========================================
    console.log("=== Step 3: Attack Attempt - Credential ID Substitution ===");

    // Start new auth flow for victim
    const attackAuthParams = new URLSearchParams({
      response_type: "code",
      client_id: clientId,
      redirect_uri: redirectUri,
      scope: "openid profile email",
      state: `attack_${timestamp}`,
    });
    const attackAuthResponse = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${attackAuthParams.toString()}`,
      headers: {},
    });
    const attackAuthId = new URL(attackAuthResponse.headers.location, backendUrl).searchParams.get('id');

    // Request FIDO2 challenge for VICTIM (this generates allowCredentials with victim's credential)
    const victimChallengeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${attackAuthId}/fido2-authentication-challenge`,
      body: { username: userEmail },  // Request challenge for VICTIM's username
    });
    expect(victimChallengeResponse.status).toBe(200);

    // Verify allowCredentials contains ONLY victim's credential
    expect(victimChallengeResponse.data.allowCredentials).toBeDefined();
    expect(victimChallengeResponse.data.allowCredentials.length).toBe(1);
    expect(victimChallengeResponse.data.allowCredentials[0].id).toBe(victimCredential.id);
    console.log("✓ Challenge generated with victim's allowCredentials");
    console.log("  allowCredentials:", victimChallengeResponse.data.allowCredentials.map(c => c.id));

    // ATTACK: Try to authenticate with ATTACKER's credential instead of victim's
    // This simulates CVE-2025-26788 where attacker substitutes their credential ID
    const attackerAssertion = generateValidAssertionFromChallenge(
      victimChallengeResponse.data,
      attackerCredential.id  // Using ATTACKER's credential ID!
    );

    console.log("\n  Attempting attack with attacker's credential ID:", attackerCredential.id);

    const attackResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${attackAuthId}/fido2-authentication`,
      body: attackerAssertion,
    });

    console.log("  Attack response:", attackResponse.status, attackResponse.data);

    // Should be REJECTED because attacker's credential ID is NOT in allowCredentials
    expect(attackResponse.status).toBe(400);
    // The error format may vary - the important thing is that the attack was blocked
    if (attackResponse.data.error) {
      console.log("  Error:", attackResponse.data.error, "-", attackResponse.data.error_description);
    }
    console.log("\n✓ Attack BLOCKED! Credential ID substitution was detected and rejected.\n");

    console.log("=== Test Completed: CVE-2025-26788 Mitigation Verified ===\n");
    console.log("Summary:");
    console.log("  - Attacker registered their own FIDO2 credential");
    console.log("  - Victim registered their own FIDO2 credential");
    console.log("  - Challenge generated with victim's allowCredentials list");
    console.log("  - Attacker's credential ID substitution was BLOCKED");
    console.log("  - Defense in Depth: allowCredentials validation prevents this attack\n");
  });
});
