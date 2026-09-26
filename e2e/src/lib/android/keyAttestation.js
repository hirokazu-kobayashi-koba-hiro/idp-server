/**
 * Builds Android key attestation chains, the way a device's KeyMint would.
 *
 * The registration endpoint accepts the certificate chain of a hardware-backed key, so exercising
 * it end to end means producing that chain. What a device produces and what this builds differ in
 * one way only: the chain leads to a root generated here rather than to Google's, which is why the
 * client under test configures `trusted_root_certificates`.
 *
 * @see https://source.android.com/docs/security/features/keystore/attestation
 */
import forge from "node-forge";

import {
  derBoolean,
  derEnumerated,
  derInteger,
  derOctetString,
  derSequence,
  derSet,
  derTagged,
} from "../der.js";

const asn1 = forge.asn1;

export const KEY_ATTESTATION_OID = "1.3.6.1.4.1.11129.2.1.17";
export const PLATFORM = "android-key-attestation";

export const SECURITY_LEVEL = {
  software: 0,
  trusted_environment: 1,
  strong_box: 2,
};

/** KM_ORIGIN_*. Only `generated` means the secure hardware created the key itself. */
export const ORIGIN = {
  generated: 0,
  derived: 1,
  imported: 2,
  unknown: 3,
  securely_imported: 4,
};

/** RootOfTrust.verifiedBootState. Only `verified` is accepted by default. */
export const VERIFIED_BOOT_STATE = {
  verified: 0,
  self_signed: 1,
  unverified: 2,
  failed: 3,
};

/** KM_PURPOSE_*. A Client Instance key has to carry `sign`. */
export const PURPOSE = {
  encrypt: 0,
  decrypt: 1,
  sign: 2,
  verify: 3,
};

/**
 * KeyDescription, with the elements the server reads.
 *
 * SecurityLevel is ENUMERATED. Encoding it as INTEGER is rejected, so `encodeSecurityLevelAsInteger`
 * exists to produce that rejection on purpose.
 *
 * `origin` and `purpose` go in `hardwareEnforced` because only KeyMint knows them; the server
 * refuses to read them from `softwareEnforced`, so `keyPropertiesInSoftwareList` can produce that
 * rejection too. `attestationApplicationId` is the opposite case and stays in `softwareEnforced`.
 *
 * `rootOfTrust` and `osPatchLevel` are KeyMint's too. By default the device booted its stock OS with
 * the bootloader locked; `rootOfTrust: null` / `osPatchLevel: 0` stand for a device that omitted them.
 */
const keyDescription = ({
  challenge,
  securityLevel,
  packageName,
  signatureDigests,
  encodeSecurityLevelAsInteger = false,
  origin = ORIGIN.generated,
  purposes = [PURPOSE.sign, PURPOSE.verify],
  keyMintSecurityLevel = securityLevel,
  keyPropertiesInSoftwareList = false,
  rootOfTrust = { verifiedBootState: VERIFIED_BOOT_STATE.verified, deviceLocked: true },
  osPatchLevel = 202409,
}) => {
  const applicationId = derSequence(
    derSet(derSequence(derOctetString(packageName), derInteger(1))),
    derSet(...signatureDigests.map((digest) => derOctetString(digest)))
  );

  // `origin: null` / `purposes: []` stand for a device that omitted the field.
  const keyProperties = [
    ...(purposes.length
      ? [derTagged(1, derSet(...purposes.map((purpose) => derInteger(purpose))))]
      : []),
    ...(origin === null ? [] : [derTagged(702, derInteger(origin))]),
    ...(rootOfTrust === null
      ? []
      : [
          derTagged(
            704,
            derSequence(
              derOctetString(Buffer.alloc(32)), // verifiedBootKey
              derBoolean(rootOfTrust.deviceLocked),
              derEnumerated(rootOfTrust.verifiedBootState),
              derOctetString(Buffer.alloc(32)) // verifiedBootHash
            )
          ),
        ]),
    ...(osPatchLevel ? [derTagged(706, derInteger(osPatchLevel))] : []),
  ];

  const softwareEnforced = derSequence(
    derTagged(709, derOctetString(applicationId)),
    ...(keyPropertiesInSoftwareList ? keyProperties : [])
  );

  const hardwareEnforced = derSequence(
    ...(keyPropertiesInSoftwareList ? [] : keyProperties)
  );

  const encodeLevel = (value) =>
    encodeSecurityLevelAsInteger ? derInteger(value) : derEnumerated(value);

  return derSequence(
    derInteger(4), // attestationVersion
    encodeLevel(securityLevel), // attestationSecurityLevel
    derInteger(4), // keyMintVersion
    encodeLevel(keyMintSecurityLevel), // keyMintSecurityLevel
    derOctetString(challenge), // attestationChallenge
    derOctetString(Buffer.alloc(0)), // uniqueId
    softwareEnforced,
    hardwareEnforced
  );
};

/**
 * @param caPathLen when present, marks the certificate as a CA allowed to issue that many CAs below
 *   itself. Google's roots carry it, and a verifier that checks the chain of trust rejects an issuer
 *   without it, so the root built here carries it too
 */
const certificate = ({
  publicKey,
  signWith,
  subject,
  issuer,
  extensionDer,
  caPathLen,
}) => {
  const cert = forge.pki.createCertificate();
  cert.publicKey = publicKey;
  cert.serialNumber = `0${Math.floor(Math.random() * 900000) + 100000}`;
  cert.validity.notBefore = new Date(Date.now() - 3600 * 1000);
  cert.validity.notAfter = new Date(Date.now() + 24 * 3600 * 1000);
  cert.setSubject([{ name: "commonName", value: subject }]);
  cert.setIssuer([{ name: "commonName", value: issuer }]);

  const extensions = [];
  if (caPathLen !== undefined) {
    extensions.push(
      {
        name: "basicConstraints",
        critical: true,
        cA: true,
        pathLenConstraint: caPathLen,
      },
      { name: "keyUsage", critical: true, keyCertSign: true, cRLSign: true }
    );
  }
  if (extensionDer) {
    extensions.push({
      id: KEY_ATTESTATION_OID,
      critical: false,
      value: extensionDer.toString("binary"),
    });
  }
  if (extensions.length > 0) {
    cert.setExtensions(extensions);
  }

  cert.sign(signWith.privateKey, forge.md.sha256.create());
  return cert;
};

const toBase64Der = (cert) =>
  forge.util.encode64(asn1.toDer(forge.pki.certificateToAsn1(cert)).getBytes());

/**
 * A big integer as base64url, the way JWK expects it.
 *
 * forge returns a two's complement byte array, which carries a leading zero byte whenever the top
 * bit is set. JWK wants the unsigned big-endian value, so that byte is dropped — stripping it from
 * the base64 text instead corrupts the number, since a byte does not map to one character.
 */
const toBase64UrlBigInteger = (bigInteger) => {
  let bytes = Buffer.from(bigInteger.toByteArray());
  while (bytes.length > 1 && bytes[0] === 0) {
    bytes = bytes.subarray(1);
  }
  return bytes.toString("base64url");
};

const toJwk = (publicKey) => ({
  kty: "RSA",
  n: toBase64UrlBigInteger(publicKey.n),
  e: toBase64UrlBigInteger(publicKey.e),
});

/** A root the test controls, standing in for Google's. */
export const generateAttestationRoot = () => {
  const keys = forge.pki.rsa.generateKeyPair(2048);
  const cert = certificate({
    publicKey: keys.publicKey,
    signWith: keys,
    subject: "attestation-root",
    issuer: "attestation-root",
    caPathLen: 1,
  });
  return { keys, certificate: cert, base64Der: toBase64Der(cert) };
};

/**
 * A chain certifying a key.
 *
 * <p>The key is supplied by the caller rather than generated here, because the same key has to sign
 * the Client Attestation JWT afterwards: the registration proves the key lives in hardware, and the
 * authentication proves the client holds it. Pass the SPKI PEM of a key whose private half the test
 * keeps.
 *
 * @returns the chain to send as `platform_evidence.x5c`
 */
export const generateAttestedKey = ({
  root,
  challenge,
  packageName,
  signatureDigest,
  publicKeyPem,
  securityLevel = SECURITY_LEVEL.trusted_environment,
  encodeSecurityLevelAsInteger = false,
  origin,
  purposes,
  keyMintSecurityLevel,
  keyPropertiesInSoftwareList,
  rootOfTrust,
  osPatchLevel,
  challengeBytes,
}) => {
  const publicKey = publicKeyPem
    ? forge.pki.publicKeyFromPem(publicKeyPem)
    : forge.pki.rsa.generateKeyPair(2048).publicKey;

  const extension = keyDescription({
    // The decoded challenge (challenge_binding "challenge"), unless another binding is embedded.
    challenge: challengeBytes ?? Buffer.from(challenge, "base64url"),
    securityLevel,
    packageName,
    signatureDigests: [signatureDigest],
    encodeSecurityLevelAsInteger,
    ...(origin !== undefined ? { origin } : {}),
    ...(purposes !== undefined ? { purposes } : {}),
    ...(keyMintSecurityLevel !== undefined ? { keyMintSecurityLevel } : {}),
    ...(keyPropertiesInSoftwareList !== undefined
      ? { keyPropertiesInSoftwareList }
      : {}),
    ...(rootOfTrust !== undefined ? { rootOfTrust } : {}),
    ...(osPatchLevel !== undefined ? { osPatchLevel } : {}),
  });

  const leaf = certificate({
    publicKey,
    signWith: root.keys,
    subject: "attested-key",
    issuer: "attestation-root",
    extensionDer: extension,
  });

  return {
    jwk: toJwk(publicKey),
    x5c: [toBase64Der(leaf), root.base64Der],
  };
};

export const platformEvidence = (x5c) => ({ platform: PLATFORM, x5c });
