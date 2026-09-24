/**
 * Android key attestation at Client Instance registration (Issue #1521).
 *
 * A registration is authenticated by an ID token (who) and the platform attestation (which device
 * and key). Every registration here carries a valid ID token, obtained in the hybrid flow with
 * nonce = request_hash, so what these tests exercise is the platform attestation alone: the chain
 * is built the way a device's KeyMint would build it, and each case removes exactly one of the
 * bindings the verifier requires.
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
  ORIGIN,
  PURPOSE,
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
  let username;
  let password;

  const REDIRECT_URI = "https://app.example.com/callback";

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

  const requestChallenge = async () => {
    const response = await postWithJson({
      url: challengesUrl(),
      body: { client_id: clientId },
    });
    expect(response.status).toBe(200);
    return response.data;
  };

  /** The ID token that authenticates a registration: obtained for this challenge and this key. */
  const idTokenFor = async ({ challenge, publicJwk }) => {
    const { idToken } = await loginForRegistration({
      tenantId,
      clientId,
      redirectUri: REDIRECT_URI,
      nonce: deriveRequestHash(challenge, publicJwk),
      username,
      password,
    });
    return idToken;
  };

  const register = async ({ challenge, instanceKey, chainOptions = {}, idTokenKey }) => {
    const idToken = await idTokenFor({
      challenge,
      publicJwk: (idTokenKey ?? instanceKey).publicJwk,
    });
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
        id_token: idToken,
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
    username = `android-attestation-${timestamp}@test.example.com`;
    password = `AndroidAttestation${timestamp}!`;

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
          response_types_supported: ["code", "code id_token"],
          response_modes_supported: ["query", "fragment"],
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
          client_name: "Android Key Attestation Client",
          token_endpoint_auth_method: "attest_jwt_client_auth",
          extension: {
            client_attestation_trust_source: "registered_instance_key",
            client_instance_registration_policy: "user_bound",
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

  /**
   * The registration endpoint answers failures with error alone, no error_description.
   *
   * <p>Every other suite here asserts the server's stated reason, because a status and an error
   * code shared by many checks cannot say which one refused. This endpoint deliberately withholds
   * that: a detailed reason would let a caller probe which clients take part in registration, or
   * which check a forged registration got past. The absence is the requirement, so it is pinned
   * rather than left to be discovered as a gap.
   */
  describe("failure responses", () => {
    it("says only that the request was invalid, never which check refused", async () => {
      const { challenge } = await requestChallenge();
      const instanceKey = await generateInstanceKey();

      const response = await register({
        challenge,
        instanceKey,
        chainOptions: { origin: ORIGIN.imported },
      });

      expect(response.status).toBe(400);
      expect(response.data).toEqual({ error: "invalid_request" });
    }, 120000);
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

    it("rejects a key that was imported rather than generated in secure hardware", async () => {
      // Every binding holds and the key lives in the TEE. A copy of the private half exists
      // wherever it was generated, so possession does not say which device is calling.
      const { challenge } = await requestChallenge();
      const instanceKey = await generateInstanceKey();

      const response = await register({
        challenge,
        instanceKey,
        chainOptions: { origin: ORIGIN.imported },
      });
      console.log("imported key:", response.status, JSON.stringify(response.data));

      expect(response.status).toBe(400);
    }, 120000);

    it("rejects a securely imported key", async () => {
      // Secure import means the plaintext never appeared on this device. The system that wrapped
      // it held the plaintext by definition.
      const { challenge } = await requestChallenge();
      const instanceKey = await generateInstanceKey();

      const response = await register({
        challenge,
        instanceKey,
        chainOptions: { origin: ORIGIN.securely_imported },
      });

      expect(response.status).toBe(400);
    }, 120000);

    it("rejects a key whose origin the device did not report", async () => {
      const { challenge } = await requestChallenge();
      const instanceKey = await generateInstanceKey();

      const response = await register({
        challenge,
        instanceKey,
        chainOptions: { origin: null },
      });

      expect(response.status).toBe(400);
    }, 120000);

    it("rejects a key KeyMint will not sign with", async () => {
      // Registering it would succeed and the first PoP would fail signature verification, on an
      // endpoint that cannot say why.
      const { challenge } = await requestChallenge();
      const instanceKey = await generateInstanceKey();

      const response = await register({
        challenge,
        instanceKey,
        chainOptions: { purposes: [PURPOSE.encrypt, PURPOSE.decrypt] },
      });

      expect(response.status).toBe(400);
    }, 120000);

    it("rejects a key held in software whose attestation was produced in hardware", async () => {
      // attestationSecurityLevel and keyMintSecurityLevel have different subjects. Reading only
      // the first accepts a software key whose attestation the TEE happened to sign.
      const { challenge } = await requestChallenge();
      const instanceKey = await generateInstanceKey();

      const response = await register({
        challenge,
        instanceKey,
        chainOptions: { keyMintSecurityLevel: SECURITY_LEVEL.software },
      });

      expect(response.status).toBe(400);
    }, 120000);

    it("does not read the key's own properties from softwareEnforced", async () => {
      // Everything a device would report, moved to the list the platform writes. Only KeyMint
      // knows these, so the chain is refused exactly as one that reported them nowhere.
      const { challenge } = await requestChallenge();
      const instanceKey = await generateInstanceKey();

      const response = await register({
        challenge,
        instanceKey,
        chainOptions: { keyPropertiesInSoftwareList: true },
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

  describe("the ID token and the evidence name the same key", () => {
    it("rejects evidence for a key other than the one the ID token was obtained for", async () => {
      // Both halves are genuine on their own: an ID token of the user, and an attested key of this
      // app. What must not happen is combining them across keys, which is what a leaked ID token
      // presented next to an attacker's own device looks like.
      const { challenge } = await requestChallenge();
      const victimKey = await generateInstanceKey();
      const attackerKey = await generateInstanceKey();

      const response = await register({ challenge, instanceKey: attackerKey, idTokenKey: victimKey });

      expect(response.status).toBe(400);
      expect(response.data).toEqual({ error: "invalid_request" });
    }, 120000);
  });

  describe("what the registered instance records", () => {
    it("keeps what the attestation established, with every certificate of the chain by serial", async () => {
      const { challenge } = await requestChallenge();
      const instanceKey = await generateInstanceKey();

      const registration = await register({ challenge, instanceKey });
      expect(registration.status).toBe(201);

      const listResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${tenantId}/clients/${clientId}/instances`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      });
      expect(listResponse.status).toBe(200);
      const registered = listResponse.data.list.find(
        (instance) => instance.id === registration.data.instance_id
      );
      const evidence = registered.attestation_evidence;

      expect(evidence.platform).toBe("android-key-attestation");
      expect(evidence.verified_at).toBeDefined();
      expect(evidence).not.toHaveProperty("binding_only");
      expect(evidence.key).toEqual({
        attestation_security_level: "trusted_environment",
        keymint_security_level: "trusted_environment",
        origin: "generated",
      });
      expect(evidence.app.package_names).toEqual([PACKAGE_NAME]);
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
