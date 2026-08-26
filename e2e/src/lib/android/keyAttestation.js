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

const asn1 = forge.asn1;

export const KEY_ATTESTATION_OID = "1.3.6.1.4.1.11129.2.1.17";
export const PLATFORM = "android-key-attestation";

export const SECURITY_LEVEL = {
  software: 0,
  trusted_environment: 1,
  strong_box: 2,
};

/**
 * A minimal DER writer.
 *
 * The extension is built as bytes rather than through node-forge's ASN.1 tree: forge writes its own
 * identifier for every node, which cannot express the [709] tag (it truncates tag numbers above 30)
 * and cannot carry pre-encoded bytes without prefixing them. Both are needed here.
 */
const tlv = (tag, content) => {
  const length = [];
  if (content.length < 128) {
    length.push(content.length);
  } else {
    const octets = [];
    let remaining = content.length;
    while (remaining > 0) {
      octets.unshift(remaining & 0xff);
      remaining >>= 8;
    }
    length.push(0x80 | octets.length, ...octets);
  }
  return Buffer.concat([Buffer.from([tag]), Buffer.from(length), content]);
};

const derInteger = (value) => tlv(0x02, Buffer.from([value]));
const derEnumerated = (value) => tlv(0x0a, Buffer.from([value]));
const derOctetString = (content) => tlv(0x04, Buffer.from(content));
const derSequence = (...parts) => tlv(0x30, Buffer.concat(parts));
const derSet = (...parts) => tlv(0x31, Buffer.concat(parts));

/**
 * A context specific constructed tag.
 *
 * attestationApplicationId is [709], which needs the high tag number form: 0xBF (context |
 * constructed | 31) followed by the tag number in base 128.
 */
const derTagged = (tagNumber, content) => {
  const identifier = [];
  if (tagNumber < 31) {
    identifier.push(0xa0 | tagNumber);
  } else {
    identifier.push(0xbf);
    const base128 = [];
    let remaining = tagNumber;
    do {
      base128.unshift(remaining & 0x7f);
      remaining >>= 7;
    } while (remaining > 0);
    base128.forEach((part, index) =>
      identifier.push(index === base128.length - 1 ? part : part | 0x80),
    );
  }

  const length = [];
  if (content.length < 128) {
    length.push(content.length);
  } else {
    const octets = [];
    let remaining = content.length;
    while (remaining > 0) {
      octets.unshift(remaining & 0xff);
      remaining >>= 8;
    }
    length.push(0x80 | octets.length, ...octets);
  }

  return Buffer.concat([Buffer.from(identifier), Buffer.from(length), content]);
};

/**
 * KeyDescription, with the elements the server reads.
 *
 * SecurityLevel is ENUMERATED. Encoding it as INTEGER is rejected, so `encodeSecurityLevelAsInteger`
 * exists to produce that rejection on purpose.
 */
const keyDescription = ({
  challenge,
  securityLevel,
  packageName,
  signatureDigests,
  encodeSecurityLevelAsInteger = false,
}) => {
  const applicationId = derSequence(
    derSet(derSequence(derOctetString(packageName), derInteger(1))),
    derSet(...signatureDigests.map((digest) => derOctetString(digest))),
  );

  const softwareEnforced = derSequence(derTagged(709, derOctetString(applicationId)));

  const level = encodeSecurityLevelAsInteger
    ? derInteger(securityLevel)
    : derEnumerated(securityLevel);

  return derSequence(
    derInteger(4), // attestationVersion
    level, // attestationSecurityLevel
    derInteger(4), // keyMintVersion
    level, // keyMintSecurityLevel
    derOctetString(challenge), // attestationChallenge
    derOctetString(Buffer.alloc(0)), // uniqueId
    softwareEnforced,
    derSequence(), // hardwareEnforced
  );
};

const certificate = ({ publicKey, signWith, subject, issuer, extensionDer }) => {
  const cert = forge.pki.createCertificate();
  cert.publicKey = publicKey;
  cert.serialNumber = `0${Math.floor(Math.random() * 900000) + 100000}`;
  cert.validity.notBefore = new Date(Date.now() - 3600 * 1000);
  cert.validity.notAfter = new Date(Date.now() + 24 * 3600 * 1000);
  cert.setSubject([{ name: "commonName", value: subject }]);
  cert.setIssuer([{ name: "commonName", value: issuer }]);
  if (extensionDer) {
    cert.setExtensions([
      { id: KEY_ATTESTATION_OID, critical: false, value: extensionDer.toString("binary") },
    ]);
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
}) => {
  const publicKey = publicKeyPem
    ? forge.pki.publicKeyFromPem(publicKeyPem)
    : forge.pki.rsa.generateKeyPair(2048).publicKey;

  const extension = keyDescription({
    challenge: Buffer.from(challenge, "base64url"),
    securityLevel,
    packageName,
    signatureDigests: [signatureDigest],
    encodeSecurityLevelAsInteger,
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
