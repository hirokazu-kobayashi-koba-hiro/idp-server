/**
 * Apple App Attest at Client Instance registration (Issue #1521).
 *
 * A registration is authenticated by an ID token (who) and the platform attestation (which device
 * and key). Every registration here carries a valid ID token, obtained in the hybrid flow with
 * nonce = request_hash, so what these tests exercise is the platform attestation alone: the
 * attestation object is built the way a device's Secure Enclave would build it, and each case
 * removes exactly one of the properties the verifier requires.
 *
 * The chain leads to a root the test generates rather than to Apple's, so the client under test
 * sets `trusted_root_certificates`. That is the one difference from a device.
 *
 * @see https://developer.apple.com/documentation/devicecheck/validating-apps-that-connect-to-your-server
 */
import { afterAll, beforeAll, describe, expect, it } from "@jest/globals";
import { v4 as uuidv4 } from "uuid";
import * as jose from "jose";
import crypto from "crypto";
import { deletion, get, postWithJson } from "../../lib/http";
import { requestToken } from "../../api/oauthClient";
import { onboarding } from "../../api/managementClient";
import { generateECP256JWKS } from "../../lib/jose";
import { createJwtWithPrivateKey, generateJti } from "../../lib/jose";
import { toEpocTime } from "../../lib/util";
import { adminServerConfig, backendUrl } from "../testConfig";
import {
  deriveRequestHash,
  enablePasswordLogin,
  loginForRegistration,
} from "../../lib/clientInstance";
import {
  ENVIRONMENT,
  generateAttestation,
  generateAttestationAuthority,
  platformEvidence,
} from "../../lib/ios/appAttest";

const ATTESTATION_HEADER = "OAuth-Client-Attestation";
const POP_HEADER = "OAuth-Client-Attestation-PoP";
const ATTESTATION_TYP = "oauth-client-attestation+jwt";
const POP_TYP = "oauth-client-attestation-pop+jwt";

const APP_ID = "ABCDE12345.com.example.wallet";

describe("Apple App Attest (Issue #1521)", () => {
  let systemAccessToken;
  let organizationId;
  let tenantId;
  let clientId;
  let issuer;
  let authority;
  let username;
  let password;

  const REDIRECT_URI = "https://app.example.com/callback";

  const challengesUrl = () =>
    `${backendUrl}/${tenantId}/v1/client-instances/challenges`;
  const instancesUrl = () => `${backendUrl}/${tenantId}/v1/client-instances`;

  /**
   * The instance key: certified by the attestation, and later the signer of the Attestation JWT.
   *
   * ES256, because App Attest generates a P-256 key in the Secure Enclave and attests that key.
   */
  const generateInstanceKey = async () => {
    const { publicKey, privateKey } = await jose.generateKeyPair("ES256", {
      extractable: true,
    });
    return {
      privateJwk: await jose.exportJWK(privateKey),
      publicJwk: await jose.exportJWK(publicKey),
      publicKeyPem: await jose.exportSPKI(publicKey),
    };
  };

  const requestChallenge = async () => {
    const response = await postWithJson({
      url: challengesUrl(),
      body: { client_id: clientId },
    });
    expect(response.status).toBe(200);
    return response.data;
  };

  /**
   * Posts a registration of instanceKey with the given attestation, authenticated by an ID token
   * obtained for this challenge and this key.
   */
  const postRegistration = async ({ challenge, instanceKey, attestationObject }) => {
    const { idToken, code } = await loginForRegistration({
      tenantId,
      clientId,
      redirectUri: REDIRECT_URI,
      nonce: deriveRequestHash(challenge, instanceKey.publicJwk),
      username,
      password,
    });

    const response = await postWithJson({
      url: instancesUrl(),
      body: {
        challenge,
        id_token: idToken,
        client_instance_public_key: instanceKey.publicJwk,
        platform_evidence: platformEvidence(attestationObject),
      },
    });
    // The code of the same login is what the app exchanges with the key it just registered.
    return { ...response, code };
  };

  const register = async ({
    challenge,
    instanceKey,
    attestationOptions = {},
  }) => {
    const attestationObject = generateAttestation({
      authority,
      challenge,
      appId: APP_ID,
      publicKeyPem: instanceKey.publicKeyPem,
      publicJwk: instanceKey.publicJwk,
      ...attestationOptions,
    });

    return await postRegistration({ challenge, instanceKey, attestationObject });
  };

  /** Registers with one property removed, and expects the registration to be refused. */
  const expectRejected = async (attestationOptions) => {
    const { challenge } = await requestChallenge();
    const instanceKey = await generateInstanceKey();

    const response = await register({
      challenge,
      instanceKey,
      attestationOptions,
    });
    expect(response.status).toBe(400);
  };

  beforeAll(async () => {
    const timestamp = Date.now();
    organizationId = uuidv4();
    tenantId = uuidv4();
    clientId = uuidv4();
    issuer = `${backendUrl}/${tenantId}`;
    authority = generateAttestationAuthority();
    username = `app-attest-${timestamp}@test.example.com`;
    password = `AppAttest${timestamp}!`;

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
          name: `App Attest Org ${timestamp}`,
          description: "E2E for #1521",
        },
        tenant: {
          id: tenantId,
          name: `App Attest Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
        },
        authorization_server: {
          issuer,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
          jwks: await generateECP256JWKS(),
          scopes_supported: ["openid", "account"],
          response_types_supported: ["code", "code id_token"],
          response_modes_supported: ["query", "fragment"],
          subject_types_supported: ["public"],
          grant_types_supported: [
            "authorization_code",
            "client_credentials",
            "password",
          ],
          id_token_signing_alg_values_supported: ["ES256"],
          token_endpoint_auth_methods_supported: [
            "attest_jwt_client_auth",
            "client_secret_post",
          ],
          // App Attest attests a P-256 key, so the instance key that signs the Attestation JWT is
          // an EC key rather than the RSA key the Android tests use.
          client_attestation_signing_alg_values_supported: ["ES256"],
          client_attestation_pop_signing_alg_values_supported: ["ES256"],
          claims_supported: ["sub"],
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
          redirect_uris: [REDIRECT_URI],
          grant_types: ["authorization_code", "client_credentials"],
          response_types: ["code", "code id_token"],
          scope: "openid account",
          client_name: "App Attest Client",
          token_endpoint_auth_method: "attest_jwt_client_auth",
          extension: {
            client_attestation_trust_source: "registered_instance_key",
            client_instance_registration_policy: "user_bound",
            client_instance_platform_config: {
              ios_app_attest: {
                app_ids: [APP_ID],
                environment: "production",
                trusted_root_certificates: [authority.rootBase64],
              },
            },
          },
        },
      },
    });
    expect(onboardingResponse.status).toBe(201);

    await enablePasswordLogin({
      tenantId,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });
  }, 120000);

  afterAll(async () => {
    if (systemAccessToken) {
      await deletion({
        url: `${backendUrl}/v1/management/orgs/${organizationId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }
  });

  describe("registration", () => {
    it("registers an instance whose key the attestation certifies", async () => {
      const { challenge } = await requestChallenge();
      const instanceKey = await generateInstanceKey();

      const response = await register({ challenge, instanceKey });
      console.log(
        "registration:",
        response.status,
        JSON.stringify(response.data).slice(0, 200)
      );

      expect(response.status).toBe(201);
      expect(response.data).toHaveProperty("instance_id");
    }, 120000);

    it("rejects an attestation produced for another challenge", async () => {
      const { challenge } = await requestChallenge();
      const instanceKey = await generateInstanceKey();

      // A valid attestation, for a challenge this registration was not issued. Accepting it would
      // let a captured attestation be replayed against a fresh challenge.
      const other = await requestChallenge();
      const attestationObject = generateAttestation({
        authority,
        challenge: other.challenge,
        appId: APP_ID,
        publicKeyPem: instanceKey.publicKeyPem,
        publicJwk: instanceKey.publicJwk,
      });

      const response = await postRegistration({ challenge, instanceKey, attestationObject });

      expect(response.status).toBe(400);
    }, 120000);

    it("rejects an attestation of a key other than the one being registered", async () => {
      const { challenge } = await requestChallenge();
      const instanceKey = await generateInstanceKey();
      const otherKey = await generateInstanceKey();

      // The attestation is valid, but it covers a key the registrant does not hold. Accepting it
      // would register a key no Secure Enclave ever vouched for.
      const attestationObject = generateAttestation({
        authority,
        challenge,
        appId: APP_ID,
        publicKeyPem: otherKey.publicKeyPem,
        publicJwk: otherKey.publicJwk,
      });

      const response = await postRegistration({ challenge, instanceKey, attestationObject });

      expect(response.status).toBe(400);
    }, 120000);

    it("rejects an attestation from another application", async () => {
      await expectRejected({ appId: "ABCDE12345.com.attacker.app" });
    }, 120000);

    it("rejects a chain that does not lead to the configured root", async () => {
      // Every binding holds here: the attacker wrote the certificate extension themselves. Only
      // the root check separates this from genuine evidence.
      await expectRejected({ signedByUntrustedRoot: true });
    }, 120000);

    it("rejects a development key when the client is configured for production", async () => {
      await expectRejected({ environment: ENVIRONMENT.development });
    }, 120000);

    it("rejects a key that has already signed an assertion", async () => {
      await expectRejected({ counter: 1 });
    }, 120000);

    it("rejects authenticator data that describes another key", async () => {
      await expectRejected({
        credentialId: crypto
          .createHash("sha256")
          .update("not-the-key")
          .digest(),
      });
    }, 120000);

    it("rejects an attestation in another format", async () => {
      await expectRejected({ format: "packed" });
    }, 120000);
  });

  describe("what the registered instance records", () => {
    it("keeps what the attestation established, with every certificate of the chain by serial", async () => {
      const { challenge } = await requestChallenge();
      const instanceKey = await generateInstanceKey();

      const registration = await register({ challenge, instanceKey });
      expect(registration.status).toBe(201);

      const listResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${tenantId}/client-instances?client_id=${clientId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      });
      expect(listResponse.status).toBe(200);
      const registered = listResponse.data.list.find(
        (instance) => instance.id === registration.data.instance_id
      );
      const evidence = registered.attestation_evidence;

      expect(evidence.platform).toBe("ios-app-attest");
      expect(evidence.verified_at).toBeDefined();
      expect(evidence.app).toEqual({ app_id: APP_ID, environment: "production" });
      // Serial numbers are what a revocation list is keyed on: lowercase hex.
      expect(evidence.chain.certificates.length).toBeGreaterThan(0);
      for (const certificate of evidence.chain.certificates) {
        expect(certificate.serial).toMatch(/^[0-9a-f]+$/);
        expect(certificate.not_after).toBeDefined();
        expect(certificate.sha256).toMatch(/^[0-9a-f]{64}$/);
      }
    }, 120000);
  });

  describe("the registered key authenticates the client", () => {
    it("issues a token for a Client Attestation JWT signed by the registered key", async () => {
      const { challenge } = await requestChallenge();
      const instanceKey = await generateInstanceKey();

      const registration = await register({ challenge, instanceKey });
      expect(registration.status).toBe(201);
      const instanceId = registration.data.instance_id;

      // registered_instance_key resolves the key by the kid of the Client Attestation JWT.
      const attestationJwt = createJwtWithPrivateKey({
        payload: {
          sub: clientId,
          iat: toEpocTime({ adjusted: 0 }),
          exp: toEpocTime({ adjusted: 300 }),
          cnf: { jwk: instanceKey.publicJwk },
        },
        privateKey: {
          ...instanceKey.privateJwk,
          kid: instanceId,
          alg: "ES256",
        },
        algorithm: "ES256",
        additionalOptions: { header: { typ: ATTESTATION_TYP } },
      });

      const popJwt = createJwtWithPrivateKey({
        payload: {
          iss: clientId,
          aud: issuer,
          jti: generateJti(),
          iat: toEpocTime({ adjusted: 0 }),
        },
        // The server resolves the verification key from the Attestation JWT's cnf, so the kid here
        // is not read. It is set because the helper always passes one to the signer.
        privateKey: {
          ...instanceKey.privateJwk,
          kid: instanceId,
          alg: "ES256",
        },
        algorithm: "ES256",
        additionalOptions: { header: { typ: POP_TYP } },
      });

      const response = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        // The instance is bound to the user who logged in, so it obtains tokens in that user's
        // context: the code of the login that authenticated the registration.
        grantType: "authorization_code",
        code: registration.code,
        redirectUri: REDIRECT_URI,
        clientId,
        additionalHeaders: {
          [ATTESTATION_HEADER]: attestationJwt,
          [POP_HEADER]: popJwt,
        },
      });
      console.log(
        "token:",
        response.status,
        JSON.stringify(response.data).slice(0, 200)
      );

      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("access_token");
    }, 120000);
  });
});
