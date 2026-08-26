/**
 * Android key attestation at Client Instance registration (Issue #1521).
 *
 * The registration endpoint is unauthenticated, so the platform attestation is what authenticates
 * the request. These tests exercise that decision through the real stack: the chain is built the
 * way a device's KeyMint would build it, and each case removes exactly one of the bindings the
 * verifier requires.
 *
 * The chain leads to a root the test generates rather than to Google's, so the client under test
 * sets `trusted_root_certificates`. That is the one difference from a device.
 *
 * @see https://source.android.com/docs/security/features/keystore/attestation
 */
import { afterAll, beforeAll, describe, expect, it } from "@jest/globals";
import { v4 as uuidv4 } from "uuid";
import * as jose from "jose";
import crypto from "crypto";
import { deletion, postWithJson } from "../../lib/http";
import { requestToken } from "../../api/oauthClient";
import { onboarding } from "../../api/managementClient";
import { generateECP256JWKS } from "../../lib/jose";
import { createJwtWithPrivateKey, generateJti } from "../../lib/jose";
import { toEpocTime } from "../../lib/util";
import { adminServerConfig, backendUrl } from "../testConfig";
import {
  SECURITY_LEVEL,
  generateAttestationRoot,
  generateAttestedKey,
  platformEvidence,
} from "../../lib/android/keyAttestation";

const ATTESTATION_HEADER = "OAuth-Client-Attestation";
const POP_HEADER = "OAuth-Client-Attestation-PoP";
const ATTESTATION_TYP = "oauth-client-attestation+jwt";
const POP_TYP = "oauth-client-attestation-pop+jwt";

const PACKAGE_NAME = "com.example.wallet";
const SIGNING_DIGEST = crypto.createHash("sha256").update("signing-certificate").digest();

describe("Android key attestation (Issue #1521)", () => {
  let systemAccessToken;
  let organizationId;
  let tenantId;
  let clientId;
  let issuer;
  let root;

  const challengesUrl = () => `${backendUrl}/${tenantId}/v1/client-instances/challenges`;
  const instancesUrl = () => `${backendUrl}/${tenantId}/v1/client-instances`;

  /** The instance key: certified by the chain, and later the signer of the Attestation JWT. */
  const generateInstanceKey = async () => {
    const { publicKey, privateKey } = await jose.generateKeyPair("RS256", { extractable: true });
    return {
      privateJwk: await jose.exportJWK(privateKey),
      publicJwk: await jose.exportJWK(publicKey),
      publicKeyPem: await jose.exportSPKI(publicKey),
    };
  };

  const requestChallenge = async ({ deviceId = uuidv4() } = {}) => {
    const response = await postWithJson({
      url: challengesUrl(),
      body: { client_id: clientId, device_id: deviceId },
    });
    expect(response.status).toBe(200);
    return response.data;
  };

  const requestChallengeWithoutDevice = async () => {
    const response = await postWithJson({
      url: challengesUrl(),
      body: { client_id: clientId },
    });
    expect(response.status).toBe(200);
    return response.data;
  };

  const register = async ({ challenge, instanceKey, chainOptions = {} }) => {
    const attested = generateAttestedKey({
      root,
      challenge,
      packageName: PACKAGE_NAME,
      signatureDigest: SIGNING_DIGEST,
      publicKeyPem: instanceKey.publicKeyPem,
      ...chainOptions,
    });

    return await postWithJson({
      url: instancesUrl(),
      body: {
        challenge,
        client_instance_public_key: instanceKey.publicJwk,
        platform_evidence: platformEvidence(attested.x5c),
      },
    });
  };

  beforeAll(async () => {
    const timestamp = Date.now();
    organizationId = uuidv4();
    tenantId = uuidv4();
    clientId = uuidv4();
    issuer = `${backendUrl}/${tenantId}`;
    root = generateAttestationRoot();

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
          name: `Android Key Attestation Org ${timestamp}`,
          description: "E2E for #1521",
        },
        tenant: {
          id: tenantId,
          name: `Android Key Attestation Tenant ${timestamp}`,
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
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          grant_types_supported: ["authorization_code", "client_credentials", "password"],
          id_token_signing_alg_values_supported: ["ES256"],
          token_endpoint_auth_methods_supported: ["attest_jwt_client_auth", "client_secret_post"],
          // The instance key is RSA because the attestation certificate is built with node-forge,
          // whose X.509 support signs and embeds RSA keys only.
          client_attestation_signing_alg_values_supported: ["RS256"],
          client_attestation_pop_signing_alg_values_supported: ["RS256"],
          claims_supported: ["sub"],
          extension: { access_token_type: "JWT" },
        },
        user: {
          sub: uuidv4(),
          provider_id: "idp-server",
          email: `android-attestation-${timestamp}@test.example.com`,
          email_verified: true,
          raw_password: `AndroidAttestation${timestamp}!`,
        },
        client: {
          client_id: clientId,
          redirect_uris: ["https://app.example.com/callback"],
          grant_types: ["authorization_code", "client_credentials"],
          response_types: ["code"],
          scope: "openid account",
          client_name: "Android Key Attestation Client",
          token_endpoint_auth_method: "attest_jwt_client_auth",
          extension: {
            client_attestation_trust_source: "registered_instance_key",
            client_instance_registration_policy: "attestation_only",
            client_instance_platform_config: {
              android_key_attestation: {
                package_names: [PACKAGE_NAME],
                signature_digests: [SIGNING_DIGEST.toString("base64url")],
                min_security_level: "trusted_environment",
                trusted_root_certificates: [root.base64Der],
              },
            },
          },
        },
      },
    });
    expect(onboardingResponse.status).toBe(201);
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
    it("registers an instance whose key the chain certifies", async () => {
      const { challenge } = await requestChallenge();
      const instanceKey = await generateInstanceKey();

      const response = await register({ challenge, instanceKey });
      console.log("registration:", response.status, JSON.stringify(response.data));

      expect(response.status).toBe(201);
      expect(response.data).toHaveProperty("instance_id");
    }, 120000);

    it("rejects evidence produced for another challenge", async () => {
      const { challenge } = await requestChallenge();
      const instanceKey = await generateInstanceKey();

      const attested = generateAttestedKey({
        root,
        challenge: Buffer.from("another-challenge").toString("base64url"),
        packageName: PACKAGE_NAME,
        signatureDigest: SIGNING_DIGEST,
        publicKeyPem: instanceKey.publicKeyPem,
      });

      const response = await postWithJson({
        url: instancesUrl(),
        body: {
          challenge,
          client_instance_public_key: instanceKey.publicJwk,
          platform_evidence: platformEvidence(attested.x5c),
        },
      });

      expect(response.status).toBe(400);
    }, 120000);

    it("rejects evidence that certifies another key", async () => {
      // A captured attestation paired with a key the attacker holds.
      const { challenge } = await requestChallenge();
      const attestedKey = await generateInstanceKey();
      const attackerKey = await generateInstanceKey();

      const attested = generateAttestedKey({
        root,
        challenge,
        packageName: PACKAGE_NAME,
        signatureDigest: SIGNING_DIGEST,
        publicKeyPem: attestedKey.publicKeyPem,
      });

      const response = await postWithJson({
        url: instancesUrl(),
        body: {
          challenge,
          client_instance_public_key: attackerKey.publicJwk,
          platform_evidence: platformEvidence(attested.x5c),
        },
      });

      expect(response.status).toBe(400);
    }, 120000);

    it("rejects another application", async () => {
      const { challenge } = await requestChallenge();
      const instanceKey = await generateInstanceKey();

      const response = await register({
        challenge,
        instanceKey,
        chainOptions: { packageName: "com.attacker.app" },
      });

      expect(response.status).toBe(400);
    }, 120000);

    it("rejects a chain that does not lead to a trusted root", async () => {
      // Internally consistent and every binding holds, because the attacker wrote the extension.
      const { challenge } = await requestChallenge();
      const instanceKey = await generateInstanceKey();
      const attackerRoot = generateAttestationRoot();

      const attested = generateAttestedKey({
        root: attackerRoot,
        challenge,
        packageName: PACKAGE_NAME,
        signatureDigest: SIGNING_DIGEST,
        publicKeyPem: instanceKey.publicKeyPem,
      });

      const response = await postWithJson({
        url: instancesUrl(),
        body: {
          challenge,
          client_instance_public_key: instanceKey.publicJwk,
          platform_evidence: platformEvidence(attested.x5c),
        },
      });

      expect(response.status).toBe(400);
    }, 120000);

    it("rejects a software backed key", async () => {
      const { challenge } = await requestChallenge();
      const instanceKey = await generateInstanceKey();

      const response = await register({
        challenge,
        instanceKey,
        chainOptions: { securityLevel: SECURITY_LEVEL.software },
      });

      expect(response.status).toBe(400);
    }, 120000);

    it("rejects a security level encoded as INTEGER instead of ENUMERATED", async () => {
      // The AOSP schema defines SecurityLevel as ENUMERATED. Accepting INTEGER would let a
      // fixture built with the wrong tag pass unnoticed.
      const { challenge } = await requestChallenge();
      const instanceKey = await generateInstanceKey();

      const response = await register({
        challenge,
        instanceKey,
        chainOptions: { encodeSecurityLevelAsInteger: true },
      });

      expect(response.status).toBe(400);
    }, 120000);
  });

  describe("registration limits", () => {
    it("rejects a second registration for a device that already holds an instance", async () => {
      // device_id is generated by the server for each authentication device registration, so a
      // reinstalled app arrives with a fresh one and never reaches here. What reaches here is a
      // second registration against a device record that already holds an instance, which would
      // otherwise leave two keys able to authenticate as the client for one device.
      const deviceId = uuidv4();

      const first = await requestChallenge({ deviceId });
      const firstKey = await generateInstanceKey();
      const firstRegistration = await register({
        challenge: first.challenge,
        instanceKey: firstKey,
      });
      expect(firstRegistration.status).toBe(201);

      const second = await requestChallenge({ deviceId });
      const secondKey = await generateInstanceKey();
      const secondRegistration = await register({
        challenge: second.challenge,
        instanceKey: secondKey,
      });

      expect(secondRegistration.status).toBe(400);
    }, 120000);

    it("does not bound the number of instances when the challenge carries no device", async () => {
      // attestation_only clients may omit device_id, and the duplicate check above is keyed on it.
      // Nothing else caps registrations, so this pins the current behaviour rather than assuming
      // it: one attested app can hold any number of instances of the same client.
      const first = await requestChallengeWithoutDevice();
      const firstKey = await generateInstanceKey();
      const firstRegistration = await register({
        challenge: first.challenge,
        instanceKey: firstKey,
      });
      expect(firstRegistration.status).toBe(201);

      const second = await requestChallengeWithoutDevice();
      const secondKey = await generateInstanceKey();
      const secondRegistration = await register({
        challenge: second.challenge,
        instanceKey: secondKey,
      });

      expect(secondRegistration.status).toBe(201);
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
        privateKey: { ...instanceKey.privateJwk, kid: instanceId, alg: "RS256" },
        algorithm: "RS256",
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
        privateKey: { ...instanceKey.privateJwk, kid: instanceId, alg: "RS256" },
        algorithm: "RS256",
        additionalOptions: { header: { typ: POP_TYP } },
      });

      const response = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        grantType: "client_credentials",
        scope: "account",
        clientId,
        additionalHeaders: {
          [ATTESTATION_HEADER]: attestationJwt,
          [POP_HEADER]: popJwt,
        },
      });
      console.log("token:", response.status, JSON.stringify(response.data).slice(0, 200));

      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("access_token");
    }, 120000);
  });
});
