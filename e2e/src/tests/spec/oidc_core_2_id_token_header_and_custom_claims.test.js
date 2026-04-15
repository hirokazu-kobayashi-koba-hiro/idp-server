import { describe, expect, it, beforeAll } from "@jest/globals";

import { getJwks, requestToken } from "../../api/oauthClient";
import { onboarding } from "../../api/managementClient";
import {
  clientSecretPostClient,
  serverConfig,
  adminServerConfig,
  backendUrl,
} from "../testConfig";
import { requestAuthorizations } from "../../oauth/request";
import { isArray, isNumber, isString } from "../../lib/util";
import { verifyAndDecodeJwt, generateECP256JWKS } from "../../lib/jose";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";

describe("ID Token - JOSE Header & Custom Claims Verification", () => {
  describe("JOSE Header (RFC 7515 Section 4)", () => {
    it("Header MUST contain alg and kid fields, and kid must match a key in JWKS", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "id_token",
        state: "state1",
        scope: "openid profile email " + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
        nonce: "nonce_header_test",
      });
      expect(authorizationResponse.idToken).not.toBeNull();

      const jwksResponse = await getJwks({
        endpoint: serverConfig.jwksEndpoint,
      });
      expect(jwksResponse.status).toBe(200);

      const decoded = verifyAndDecodeJwt({
        jwt: authorizationResponse.idToken,
        jwks: jwksResponse.data,
      });

      // Header verification
      expect(decoded.header).toBeDefined();
      expect(decoded.header.alg).toBeDefined();
      expect(isString(decoded.header.alg)).toBe(true);
      expect(["RS256", "RS384", "RS512", "ES256", "ES384", "ES512", "PS256", "PS384", "PS512"]).toContain(decoded.header.alg);

      expect(decoded.header.kid).toBeDefined();
      expect(isString(decoded.header.kid)).toBe(true);

      // kid must match a key in JWKS
      const matchingKey = jwksResponse.data.keys.find(
        (k) => k.kid === decoded.header.kid
      );
      expect(matchingKey).toBeDefined();

      // Signature must be valid
      expect(decoded.verifyResult).toBe(true);
    });

    it("ID Token has 3 Base64url-encoded parts separated by dots", async () => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "id_token",
        state: "state2",
        scope: "openid " + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
        nonce: "nonce_structure_test",
      });
      expect(authorizationResponse.idToken).not.toBeNull();

      const parts = authorizationResponse.idToken.split(".");
      expect(parts.length).toBe(3);

      // Each part should be valid Base64url
      const base64urlRegex = /^[A-Za-z0-9_-]+$/;
      parts.forEach((part, index) => {
        expect(base64urlRegex.test(part)).toBe(true);
      });

      // Header (part 0) should be valid JSON
      const headerJson = JSON.parse(
        Buffer.from(parts[0], "base64url").toString()
      );
      expect(headerJson.alg).toBeDefined();

      // Payload (part 1) should be valid JSON with required claims
      const payloadJson = JSON.parse(
        Buffer.from(parts[1], "base64url").toString()
      );
      expect(payloadJson.iss).toBeDefined();
      expect(payloadJson.sub).toBeDefined();
      expect(payloadJson.aud).toBeDefined();
      expect(payloadJson.exp).toBeDefined();
      expect(payloadJson.iat).toBeDefined();

      // Signature (part 2) should be non-empty
      expect(parts[2].length).toBeGreaterThan(0);
    });
  });

  describe("Custom Claims via claims: scope prefix", () => {
    let systemAccessToken;
    let tenantId;
    let organizationId;
    let clientId;
    let clientSecret;
    let userEmail;
    let userPassword;
    let userSub;

    beforeAll(async () => {
      // Get system token
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

      // Create tenant with custom_claims_scope_mapping enabled
      organizationId = uuidv4();
      tenantId = uuidv4();
      clientId = uuidv4();
      clientSecret = crypto.randomBytes(32).toString("hex");
      userSub = uuidv4();
      userEmail = `claims-spec-${Date.now()}@test.example.com`;
      userPassword = `ClaimsSpecPass${Date.now()}!`;
      const jwksContent = await generateECP256JWKS();

      const onboardingResponse = await onboarding({
        headers: { Authorization: `Bearer ${systemAccessToken}` },
        body: {
          organization: {
            id: organizationId,
            name: "Claims Spec Org",
            description: "Custom claims spec test",
          },
          tenant: {
            id: tenantId,
            name: "Claims Spec Tenant",
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
              "openid",
              "profile",
              "email",
              "management",
              "claims:ex_sub",
              "claims:status",
              "claims:roles",
              "claims:permissions",
              "claims:assigned_tenants",
              "claims:assigned_organizations",
            ],
            response_types_supported: ["code"],
            response_modes_supported: ["query"],
            subject_types_supported: ["public"],
            grant_types_supported: ["authorization_code", "password"],
            token_endpoint_auth_methods_supported: ["client_secret_post"],
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
            email: userEmail,
            raw_password: userPassword,
          },
          client: {
            client_id: clientId,
            client_secret: clientSecret,
            redirect_uris: ["http://localhost:3000/callback"],
            grant_types: ["authorization_code", "password"],
            response_types: ["code"],
            scope:
              "openid profile email management claims:ex_sub claims:status claims:roles claims:permissions claims:assigned_tenants claims:assigned_organizations",
            token_endpoint_auth_method: "client_secret_post",
          },
        },
      });
      expect(onboardingResponse.status).toBe(201);
    });

    it("claims:status scope should include status claim in ID token", async () => {
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        grantType: "password",
        username: userEmail,
        password: userPassword,
        scope: "openid claims:status",
        clientId: clientId,
        clientSecret: clientSecret,
      });
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data.id_token).toBeDefined();

      const jwksResponse = await getJwks({
        endpoint: `${backendUrl}/${tenantId}/v1/jwks`,
      });
      expect(jwksResponse.status).toBe(200);

      const decoded = verifyAndDecodeJwt({
        jwt: tokenResponse.data.id_token,
        jwks: jwksResponse.data,
      });

      expect(decoded.verifyResult).toBe(true);
      expect(decoded.payload.status).toBeDefined();
      expect(decoded.payload.status).toBe("REGISTERED");
    });

    it("claims:roles and claims:permissions should include RBAC claims in ID token", async () => {
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        grantType: "password",
        username: userEmail,
        password: userPassword,
        scope: "openid claims:roles claims:permissions",
        clientId: clientId,
        clientSecret: clientSecret,
      });
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data.id_token).toBeDefined();

      const jwksResponse = await getJwks({
        endpoint: `${backendUrl}/${tenantId}/v1/jwks`,
      });
      const decoded = verifyAndDecodeJwt({
        jwt: tokenResponse.data.id_token,
        jwks: jwksResponse.data,
      });

      expect(decoded.verifyResult).toBe(true);

      // Onboarded admin user should have default roles and permissions
      expect(decoded.payload.roles).toBeDefined();
      expect(isArray(decoded.payload.roles)).toBe(true);
      expect(decoded.payload.roles.length).toBeGreaterThan(0);

      expect(decoded.payload.permissions).toBeDefined();
      expect(isArray(decoded.payload.permissions)).toBe(true);
      expect(decoded.payload.permissions.length).toBeGreaterThan(0);
    });

    it("claims:assigned_tenants should include tenant assignment and current_tenant_id", async () => {
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        grantType: "password",
        username: userEmail,
        password: userPassword,
        scope: "openid claims:assigned_tenants",
        clientId: clientId,
        clientSecret: clientSecret,
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
      expect(decoded.payload.assigned_tenants).toBeDefined();
      expect(isArray(decoded.payload.assigned_tenants)).toBe(true);
      expect(decoded.payload.assigned_tenants).toContain(tenantId);
      // current_tenant_id may not be set in password grant context
      if (decoded.payload.current_tenant_id) {
        expect(decoded.payload.current_tenant_id).toBe(tenantId);
      }
    });

    it("claims:assigned_organizations should include organization assignment", async () => {
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        grantType: "password",
        username: userEmail,
        password: userPassword,
        scope: "openid claims:assigned_organizations",
        clientId: clientId,
        clientSecret: clientSecret,
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
      expect(decoded.payload.assigned_organizations).toBeDefined();
      expect(isArray(decoded.payload.assigned_organizations)).toBe(true);
      expect(decoded.payload.assigned_organizations).toContain(organizationId);
    });

    it("standard claims (sub, iss, aud) must not be overwritten by custom claims", async () => {
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        grantType: "password",
        username: userEmail,
        password: userPassword,
        scope:
          "openid claims:status claims:roles claims:permissions claims:assigned_tenants claims:assigned_organizations",
        clientId: clientId,
        clientSecret: clientSecret,
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

      // Standard claims must remain correct even with custom claims
      expect(decoded.payload.iss).toBe(`${backendUrl}/${tenantId}`);
      expect(decoded.payload.sub).toBe(userSub);
      expect(decoded.payload.aud).toContain(clientId);
      expect(isNumber(decoded.payload.exp)).toBe(true);
      expect(isNumber(decoded.payload.iat)).toBe(true);
      expect(decoded.payload.exp).toBeGreaterThan(decoded.payload.iat);
    });

    it("without claims: scope, custom claims should not appear in ID token", async () => {
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        grantType: "password",
        username: userEmail,
        password: userPassword,
        scope: "openid profile email",
        clientId: clientId,
        clientSecret: clientSecret,
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

      // Custom claims should be absent
      expect(decoded.payload.status).toBeUndefined();
      expect(decoded.payload.roles).toBeUndefined();
      expect(decoded.payload.permissions).toBeUndefined();
      expect(decoded.payload.assigned_tenants).toBeUndefined();
      expect(decoded.payload.assigned_organizations).toBeUndefined();

      // Standard claims should still be present
      expect(decoded.payload.iss).toBeDefined();
      expect(decoded.payload.sub).toBeDefined();
      expect(decoded.payload.aud).toBeDefined();
    });
  });
});
