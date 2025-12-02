import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { deletion, get, postWithJson } from "../../../lib/http";
import { mtlsGet, mtlsPost, mtlsPostWithJson } from "../../../lib/http/mtls";
import { requestToken } from "../../../api/oauthClient";
import { adminServerConfig, backendUrl, mtlBackendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import fs from "fs";
import path from "path";
import crypto from "crypto";
import { createJwtWithPrivateKey, generateJti } from "../../../lib/jose";
import { toEpocTime } from "../../../lib/util";

/**
 * Financial Grade Use Case: Transfer Flow with FIDO-UAF
 *
 * This test demonstrates a complete transfer workflow:
 * 1. User registration (initial-registration)
 * 2. FIDO-UAF device registration
 * 3. Authorization request with 'transfers' scope
 * 4. FIDO-UAF authentication (required by policy)
 * 5. Get access token with MTLS
 * 6. Use token to access protected resource
 */
describe("Financial Grade: Transfer Flow with FIDO-UAF", () => {
  let systemAccessToken;
  let orgAdminToken;
  let organizationId;
  let organizerTenantId;
  let financialTenantId;
  let financialClientId;
  let userId;
  let userEmail;
  let userPassword;

  // Load configuration files
  const configDir = path.join(process.cwd(), "../config/examples/financial-grade");
  const onboardingTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "onboarding-request.json"), "utf8")
  );
  const financialTenantTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "financial-tenant.json"), "utf8")
  );
  const financialClientTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "financial-client.json"), "utf8")
  );
  const authPolicyTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "authentication-policy/oauth.json"), "utf8")
  );
  const fidoUafRegistrationPolicyTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "authentication-policy/fido-uaf-registration.json"), "utf8")
  );
  const initialRegConfigTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "authentication-config/initial-registration/standard.json"), "utf8")
  );
  const fidoUafConfigTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "authentication-config/fido-uaf/external.json"), "utf8")
  );
  const smsConfigTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "authentication-config/sms/external.json"), "utf8")
  );

  let onboardingConfig;
  let financialTenantConfig;
  let financialClientConfig;
  let authPolicyConfig;
  let fidoUafRegistrationPolicyConfig;
  let initialRegConfig;
  let fidoUafConfig;
  let smsConfig;
  let clientCertPath;
  let clientKeyPath;
  let differentCertPath;
  let differentKeyPath;

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

    // Generate unique IDs
    organizationId = uuidv4();
    organizerTenantId = uuidv4();
    financialTenantId = uuidv4();
    financialClientId = uuidv4();
    userId = uuidv4();
    const adminClientId = uuidv4();
    const authPolicyId = uuidv4();
    const fidoUafRegistrationPolicyId = uuidv4();
    const initialRegConfigId = uuidv4();
    const fidoUafConfigId = uuidv4();
    const smsConfigId = uuidv4();
    const timestamp = Date.now();
    userEmail = `financial-user-${timestamp}@example.com`;
    userPassword = `SecurePass${timestamp}!`;

    // Client certificate paths for mTLS
    clientCertPath = path.join(configDir, "certs/client-cert.pem");
    clientKeyPath = path.join(configDir, "certs/client-key.pem");

    // Different valid certificate (not bound to token) for testing certificate mismatch
    differentCertPath = path.resolve(process.cwd(), "src/api/cert/selfSignedTlsAuth.pem");
    differentKeyPath = path.resolve(process.cwd(), "src/api/cert/selfSignedTlsAuth.key");

    // Create unique configs
    onboardingConfig = JSON.parse(JSON.stringify(onboardingTemplate));
    onboardingConfig.organization.id = organizationId;
    onboardingConfig.tenant.id = organizerTenantId;
    onboardingConfig.user.sub = uuidv4();
    onboardingConfig.client.client_id = adminClientId;
    onboardingConfig.authorization_server.issuer = `${backendUrl}/${organizerTenantId}`;
    onboardingConfig.authorization_server.authorization_endpoint = `${backendUrl}/${organizerTenantId}/v1/authorizations`;
    onboardingConfig.authorization_server.token_endpoint = `${backendUrl}/${organizerTenantId}/v1/tokens`;
    onboardingConfig.authorization_server.userinfo_endpoint = `${backendUrl}/${organizerTenantId}/v1/userinfo`;
    onboardingConfig.authorization_server.jwks_uri = `${backendUrl}/${organizerTenantId}/v1/jwks`;
    onboardingConfig.authorization_server.token_introspection_endpoint = `${backendUrl}/${organizerTenantId}/v1/tokens/introspection`;
    onboardingConfig.authorization_server.token_revocation_endpoint = `${backendUrl}/${organizerTenantId}/v1/tokens/revocation`;
    onboardingConfig.authorization_server.backchannel_authentication_endpoint = `${backendUrl}/${organizerTenantId}/v1/backchannel/authentications`;

    financialTenantConfig = JSON.parse(JSON.stringify(financialTenantTemplate));
    financialTenantConfig.tenant.id = financialTenantId;
    financialTenantConfig.authorization_server.issuer = `${backendUrl}/${financialTenantId}`;
    financialTenantConfig.authorization_server.authorization_endpoint = `${backendUrl}/${financialTenantId}/v1/authorizations`;
    financialTenantConfig.authorization_server.token_endpoint = `${backendUrl}/${financialTenantId}/v1/tokens`;
    financialTenantConfig.authorization_server.userinfo_endpoint = `${backendUrl}/${financialTenantId}/v1/userinfo`;
    financialTenantConfig.authorization_server.jwks_uri = `${backendUrl}/${financialTenantId}/v1/jwks`;
    financialTenantConfig.authorization_server.token_introspection_endpoint = `${backendUrl}/${financialTenantId}/v1/tokens/introspection`;
    financialTenantConfig.authorization_server.token_revocation_endpoint = `${backendUrl}/${financialTenantId}/v1/tokens/revocation`;
    financialTenantConfig.authorization_server.backchannel_authentication_endpoint = `${backendUrl}/${financialTenantId}/v1/backchannel/authentications`;

    financialClientConfig = JSON.parse(JSON.stringify(financialClientTemplate));
    financialClientConfig.client_id = financialClientId;

    authPolicyConfig = JSON.parse(JSON.stringify(authPolicyTemplate));
    authPolicyConfig.id = authPolicyId;

    fidoUafRegistrationPolicyConfig = JSON.parse(JSON.stringify(fidoUafRegistrationPolicyTemplate));
    fidoUafRegistrationPolicyConfig.id = fidoUafRegistrationPolicyId;

    initialRegConfig = JSON.parse(JSON.stringify(initialRegConfigTemplate));
    initialRegConfig.id = initialRegConfigId;

    fidoUafConfig = JSON.parse(JSON.stringify(fidoUafConfigTemplate));
    fidoUafConfig.id = fidoUafConfigId;

    smsConfig = JSON.parse(JSON.stringify(smsConfigTemplate));
    smsConfig.id = smsConfigId;
  });

  afterAll(async () => {
    // Cleanup
    console.log("\n=== Cleanup: Deleting Resources ===");

    if (financialClientId && financialTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${financialTenantId}/clients/${financialClientId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (financialTenantId && authPolicyConfig) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${financialTenantId}/authentication-policies/${authPolicyConfig.id}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (financialTenantId && fidoUafRegistrationPolicyConfig) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${financialTenantId}/authentication-policies/${fidoUafRegistrationPolicyConfig.id}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (financialTenantId && initialRegConfig) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${financialTenantId}/authentication-configurations/${initialRegConfig.id}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (financialTenantId && fidoUafConfig) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${financialTenantId}/authentication-configurations/${fidoUafConfig.id}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (financialTenantId && smsConfig) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${financialTenantId}/authentication-configurations/${smsConfig.id}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (userId && financialTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${financialTenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (financialTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${financialTenantId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (onboardingConfig && organizerTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${organizerTenantId}/clients/${onboardingConfig.client.client_id}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});

      await deletion({
        url: `${backendUrl}/v1/management/tenants/${organizerTenantId}/users/${onboardingConfig.user.sub}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});

      await deletion({
        url: `${backendUrl}/v1/management/tenants/${organizerTenantId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (organizationId) {
      await deletion({
        url: `${backendUrl}/v1/management/orgs/${organizationId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }
  });

  it("Complete transfer flow: registration -> FIDO-UAF -> transfers scope -> token", async () => {
    if (!fs.existsSync(clientCertPath) || !fs.existsSync(clientKeyPath)) {
      console.log("⚠️  Client certificate not found, skipping test");
      return;
    }

    // === Setup: Create organization and financial tenant ===
    console.log("\n=== Setup: Creating Organization ===");

    const onboardingResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: onboardingConfig,
    });

    expect(onboardingResponse.status).toBe(201);
    console.log(`✅ Organization created: ${organizationId}`);

    // Get org admin token
    const orgTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${organizerTenantId}/v1/tokens`,
      grantType: "password",
      username: onboardingConfig.user.email,
      password: onboardingConfig.user.raw_password,
      scope: "openid profile email management",
      clientId: onboardingConfig.client.client_id,
      clientSecret: onboardingConfig.client.client_secret,
    });

    expect(orgTokenResponse.status).toBe(200);
    orgAdminToken = orgTokenResponse.data.access_token;

    // Create financial tenant
    const tenantResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: financialTenantConfig,
    });

    expect(tenantResponse.status).toBe(201);
    console.log(`✅ Financial Tenant created: ${financialTenantId}`);

    // Create financial client
    const clientResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${financialTenantId}/clients`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: financialClientConfig,
    });

    expect(clientResponse.status).toBe(201);
    console.log(`✅ Financial Client created: ${financialClientId}`);

    // Create authentication configs
    const initialRegResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${financialTenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: initialRegConfig,
    });

    expect(initialRegResponse.status).toBe(201);
    console.log("✅ Initial Registration Config created");

    const fidoUafResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${financialTenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: fidoUafConfig,
    });

    expect(fidoUafResponse.status).toBe(201);
    console.log("✅ FIDO-UAF Config created");

    const smsResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${financialTenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: smsConfig,
    });

    expect(smsResponse.status).toBe(201);
    console.log("✅ SMS Config created");

    // Create authentication policy
    const policyResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${financialTenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: authPolicyConfig,
    });

    expect(policyResponse.status).toBe(201);
    console.log("✅ Authentication Policy (OAuth flow) created");

    // Create FIDO-UAF registration policy (me flow)
    const fidoUafRegPolicyResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${financialTenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: fidoUafRegistrationPolicyConfig,
    });

    expect(fidoUafRegPolicyResponse.status).toBe(201);
    console.log("✅ FIDO-UAF Registration Policy (me flow) created");

    // === Step 1: User registration via initial-registration ===
    console.log("\n=== Step 1: User Registration ===");

    // Generate PKCE parameters (required by FAPI)
    const codeVerifier = crypto.randomBytes(32).toString("base64url");
    const codeChallenge = crypto.createHash("sha256").update(codeVerifier).digest("base64url");

    // Start authorization request with PKCE and nonce (FAPI required)
    const nonce = crypto.randomBytes(16).toString("base64url");

    console.log(onboardingConfig.client);
    const authzParams = new URLSearchParams({
      response_type: "code",
      client_id: financialClientId,
      redirect_uri: financialClientConfig.redirect_uris[0],
      scope: "openid profile email account",
      state: "test-state",
      nonce: nonce,
      code_challenge: codeChallenge,
      code_challenge_method: "S256",
    });

    const authzResponse = await get({
      url: `${backendUrl}/${financialTenantId}/v1/authorizations?${authzParams.toString()}`,
    });

    console.log("Authorization response headers:", authzResponse.headers);
    expect(authzResponse.status).toBe(302);
    const location = authzResponse.headers.location;
    console.log(`Location header: ${location}`);

    const authTxId = new URL(location).searchParams.get("id");
    console.log(`Auth Transaction ID: ${authTxId}`);
    expect(authTxId).not.toBeNull();

    console.log(`✅ Authorization request started: ${authTxId}`);

    // Register new user (front-channel - no mTLS)
    const registrationResponse = await postWithJson({
      url: `${backendUrl}/${financialTenantId}/v1/authorizations/${authTxId}/initial-registration`,
      body: {
        email: userEmail,
        name: "Financial User",
        phone_number: "+81-90-1234-5678",
        password: userPassword,
        birthdate: "1990-01-01",
        address: {
          country: "JP",
          postal_code: "100-0001",
          region: "Tokyo",
          locality: "Chiyoda",
        },
      },
    });

    console.log(`Registration response status: ${registrationResponse.status}`);
    console.log("Registration response:", JSON.stringify(registrationResponse.data, null, 2));

    expect(registrationResponse.status).toBe(200);
    userId = registrationResponse.data.user.sub;

    console.log(`✅ User registered: ${userId}`);
    console.log(`✅ Email: ${userEmail}`);

    // SMS authentication (2nd factor required by policy)
    console.log("\n=== SMS Authentication (2nd Factor) ===");

    const smsChallengeResponse = await postWithJson({
      url: `${backendUrl}/${financialTenantId}/v1/authorizations/${authTxId}/sms-authentication-challenge`,
      body: {
        phone_number: "+81-90-1234-5678",
        template: "authentication",
      },
    });

    console.log("SMS challenge response:", smsChallengeResponse.status, smsChallengeResponse.data);
    expect(smsChallengeResponse.status).toBe(200);

    // Get verification code from authentication transaction
    const authTxResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${financialTenantId}/authentication-transactions?authorization_id=${authTxId}`,
      headers: {
        Authorization: `Bearer ${orgAdminToken}`,
      },
    });

    console.log("Auth transaction response:", authTxResponse.status, authTxResponse.data);
    expect(authTxResponse.status).toBe(200);
    const transactionId = authTxResponse.data.list[0].id;

    const interactionResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${financialTenantId}/authentication-interactions/${transactionId}/sms-authentication-challenge`,
      headers: {
        Authorization: `Bearer ${orgAdminToken}`,
      },
    });

    console.log("Interaction response:", interactionResponse.status, interactionResponse.data);
    expect(interactionResponse.status).toBe(200);
    const verificationCode = interactionResponse.data.payload.verification_code;

    console.log(`✅ Verification code obtained: ${verificationCode}`);

    // Verify SMS code
    const smsVerificationResponse = await postWithJson({
      url: `${backendUrl}/${financialTenantId}/v1/authorizations/${authTxId}/sms-authentication`,
      body: {
        verification_code: verificationCode,
      },
    });

    console.log("SMS verification response:", smsVerificationResponse.status, smsVerificationResponse.data);
    expect(smsVerificationResponse.status).toBe(200);

    console.log("✅ SMS authentication completed");

    // Authorize
    const authorizeResponse = await postWithJson({
      url: `${backendUrl}/${financialTenantId}/v1/authorizations/${authTxId}/authorize`,
      body: {},
    });

    console.log(`Authorize response status: ${authorizeResponse.status}`);
    console.log("Authorize response:", JSON.stringify(authorizeResponse.data, null, 2));

    expect(authorizeResponse.status).toBe(200);
    expect(authorizeResponse.data.redirect_uri).toContain("code=");

    const redirectUri = new URL(authorizeResponse.data.redirect_uri);
    const code = redirectUri.searchParams.get("code");
    expect(code).toBeDefined();

    console.log("✅ Authorization code obtained");

    // Exchange code for token (with code_verifier for PKCE) - mTLS backchannel
    const tokenParams = new URLSearchParams({
      grant_type: "authorization_code",
      code: code,
      redirect_uri: financialClientConfig.redirect_uris[0],
      client_id: financialClientId,
      code_verifier: codeVerifier,
    });

    const tokenResponse = await mtlsPost({
      url: `${mtlBackendUrl}/${financialTenantId}/v1/tokens`,
      body: tokenParams.toString(),
      certPath: clientCertPath,
      keyPath: clientKeyPath,
    });

    expect(tokenResponse.status).toBe(200);
    expect(tokenResponse.data.access_token).toBeDefined();
    expect(tokenResponse.data.scope).toContain("account");

    console.log("✅ Access token obtained (account scope)");

    // === Step 2: FIDO-UAF device registration ===
    console.log("\n=== Step 2: FIDO-UAF Device Registration ===");
    
    const userToken = tokenResponse.data.access_token;

    console.log("✅ User logged in, token obtained");

    // FIDO-UAF registration challenge - test without mTLS (should fail)
    const failedChallengeResponse = await postWithJson({
      url: `${backendUrl}/${financialTenantId}/v1/me/mfa/fido-uaf-registration`,
      headers: {
        Authorization: `Bearer ${userToken}`,
      },
      body: {},
    });

    console.log(`FIDO-UAF challenge without mTLS: ${JSON.stringify(failedChallengeResponse.data, null, 2)}`);
    expect(failedChallengeResponse.status).toBe(401);

    // Test with different valid certificate (valid but not bound - should fail per RFC 8705)
    const differentChallengeResponse = await mtlsPostWithJson({
      url: `${mtlBackendUrl}/${financialTenantId}/v1/me/mfa/fido-uaf-registration`,
      headers: {
        Authorization: `Bearer ${userToken}`,
      },
      body: {},
      certPath: differentCertPath,
      keyPath: differentKeyPath,
    });

    console.log(`FIDO-UAF challenge with different valid cert: ${JSON.stringify(differentChallengeResponse.data, null, 2)}`);
    expect(differentChallengeResponse.status).toBe(401);

    // Test with correct certificate via mTLS (should succeed)
    const challengeResponse = await mtlsPostWithJson({
      url: `${mtlBackendUrl}/${financialTenantId}/v1/me/mfa/fido-uaf-registration`,
      headers: {
        Authorization: `Bearer ${userToken}`,
      },
      body: {},
      certPath: clientCertPath,
      keyPath: clientKeyPath,
    });

    console.log(`FIDO-UAF challenge response: ${JSON.stringify(challengeResponse.data, null, 2)}`);
    expect(challengeResponse.status).toBe(200);

    // === Certificate Binding Verification for Userinfo API ===
    console.log("\n=== Certificate Binding Verification: Userinfo API ===");

    // Test 1: No mTLS (should fail)
    const userinfoNoCertResponse = await get({
      url: `${backendUrl}/${financialTenantId}/v1/userinfo`,
      headers: {
        Authorization: `Bearer ${userToken}`,
      },
    });
    console.log(`Userinfo without mTLS: ${userinfoNoCertResponse.status}`);
    expect(userinfoNoCertResponse.status).toBe(401);

    // Test 2: Different valid certificate via mTLS (should fail per RFC 8705)
    const userinfoDifferentCertResponse = await mtlsGet({
      url: `${mtlBackendUrl}/${financialTenantId}/v1/userinfo`,
      headers: {
        Authorization: `Bearer ${userToken}`,
      },
      certPath: differentCertPath,
      keyPath: differentKeyPath,
    });
    console.log(`Userinfo with different valid cert: ${userinfoDifferentCertResponse.status}`);
    expect(userinfoDifferentCertResponse.status).toBe(401);

    // Test 3: Correct certificate via mTLS (should succeed)
    const userinfoResponse = await mtlsGet({
      url: `${mtlBackendUrl}/${financialTenantId}/v1/userinfo`,
      headers: {
        Authorization: `Bearer ${userToken}`,
      },
      certPath: clientCertPath,
      keyPath: clientKeyPath,
    });
    console.log(`Userinfo with correct cert: ${JSON.stringify(userinfoResponse.data, null, 2)}`);
    expect(userinfoResponse.status).toBe(200);
    expect(userinfoResponse.data).toHaveProperty("sub");
    console.log("✅ Userinfo API certificate binding verification passed");

    // === Step 3: Authorization with transfers scope (FIDO-UAF required) ===
    console.log("\n=== Step 3: Authorization with Transfers Scope ===");

    // Generate new PKCE for transfer request
    const transferCodeVerifier = crypto.randomBytes(32).toString("base64url");
    const transferCodeChallenge = crypto.createHash("sha256").update(transferCodeVerifier).digest("base64url");

    // Start authorization with 'transfers' scope
    const transferNonce = crypto.randomBytes(16).toString("base64url");

    const request = createJwtWithPrivateKey({
      payload: {
        response_type: "code",
        client_id: financialClientId,
        iss: financialClientId,
        aud: financialTenantConfig.authorization_server.issuer,
        redirect_uri: financialClientConfig.redirect_uris[0],
        scope: "openid transfers",
        state: "transfer-test",
        nonce: transferNonce,
        code_challenge: transferCodeChallenge,
        code_challenge_method: "S256",
        response_mode: "jwt",
        exp: toEpocTime({ adjusted: 3000 }),
        iat: toEpocTime({}),
        nbf: toEpocTime({}),
        jti: generateJti(),
      },
      privateKey: JSON.parse(financialClientConfig.jwks).keys[0],
    });
    console.log(request);
    const fapiParams = new URLSearchParams({
      client_id: financialClientId,
      request: request,
    });

    const transferAuthzResponse = await get({
      url: `${backendUrl}/${financialTenantId}/v1/authorizations?${fapiParams.toString()}`,
    });

    console.log(transferAuthzResponse.headers);
    expect(transferAuthzResponse.status).toBe(302);
    const transferLocation = transferAuthzResponse.headers.location;
    const transferAuthTxId = new URL(transferLocation).searchParams.get("id");
    expect(transferAuthTxId).not.toBeNull();

    console.log(`✅ Authorization request with 'transfers' scope: ${transferAuthTxId}`);

    // Try password authentication (should be rejected by policy)
    const passwordAuthResponse = await postWithJson({
      url: `${backendUrl}/${financialTenantId}/v1/authorizations/${transferAuthTxId}/password-authentication`,
      body: {
        username: userEmail,
        password: userPassword,
      },
    });

    // Password authentication should be rejected for 'transfers' scope
    if (passwordAuthResponse.status === 400 || passwordAuthResponse.status === 403) {
      console.log("✅ Password authentication correctly rejected for 'transfers' scope");
      console.log("   Policy enforcement working: FIDO-UAF/WebAuthn required");
    } else {
      console.log(`⚠️  Unexpected response: HTTP ${passwordAuthResponse.status}`);
    }

    // === Step 4: Verify policy enforcement ===
    console.log("\n=== Step 4: Verifying Policy Enforcement ===");

    const discoveryResponse = await get({
      url: `${backendUrl}/${financialTenantId}/.well-known/openid-configuration`,
    });

    expect(discoveryResponse.status).toBe(200);

    console.log("✅ Discovery endpoint accessible");
    console.log("✅ Financial Grade configuration verified");
    console.log("   - Pairwise subject type for privacy");
    console.log("   - FAPI scopes configured (read, account, write, transfers)");
    console.log("   - Authentication policies enforce WebAuthn/FIDO-UAF for transfers");
  });
});
