/**
 * Builds Apple App Attest attestation objects, the way a device's Secure Enclave would.
 *
 * The registration endpoint accepts the attestation object `DCAppAttestService` produces, so
 * exercising it end to end means producing one. What a device produces and what this builds differ
 * in one way only: the chain leads to a root generated here rather than to Apple's, which is why
 * the client under test configures `trusted_root_certificates`.
 *
 * Certificates are assembled as DER rather than through node-forge, which signs with RSA only
 * while App Attest keys are EC P-256.
 *
 * @see https://developer.apple.com/documentation/devicecheck/validating-apps-that-connect-to-your-server
 */
import crypto from "crypto";
import cbor from "cbor";

import {
  derBitString,
  derInteger,
  derOctetString,
  derOid,
  derSequence,
  derSet,
  derTagged,
  derUtcTime,
  derUtf8String,
} from "../der.js";

export const PLATFORM = "ios-app-attest";
export const APP_ATTEST_NONCE_OID = "1.2.840.113635.100.8.2";

const ECDSA_WITH_SHA256_OID = "1.2.840.10045.4.3.2";
const COMMON_NAME_OID = "2.5.4.3";

/** "appattest" padded to 16 bytes, and the 16 character development name. */
export const ENVIRONMENT = {
  production: Buffer.concat([
    Buffer.from("appattest", "ascii"),
    Buffer.alloc(7),
  ]),
  development: Buffer.from("appattestdevelop", "ascii"),
};

const sha256 = (input) => crypto.createHash("sha256").update(input).digest();

const name = (commonName) =>
  derSequence(
    derSet(derSequence(derOid(COMMON_NAME_OID), derUtf8String(commonName)))
  );

const pemToDer = (pem) =>
  Buffer.from(
    pem.replace(/-----(BEGIN|END)[^-]+-----/g, "").replace(/\s/g, ""),
    "base64"
  );

/**
 * An X.509 certificate.
 *
 * @param nonce when present, added as the App Attest nonce extension, which is the only extension
 *   the verifier reads
 */
const certificate = ({
  subjectPublicKeyInfo,
  subject,
  issuer,
  issuerPrivateKey,
  nonce,
}) => {
  const now = Date.now();

  const parts = [
    derTagged(0, derInteger(2)), // version v3
    derInteger(BigInt(`0x${crypto.randomBytes(8).toString("hex")}`)),
    derSequence(derOid(ECDSA_WITH_SHA256_OID)),
    name(issuer),
    derSequence(
      derUtcTime(new Date(now - 3600e3)),
      derUtcTime(new Date(now + 86400e3))
    ),
    name(subject),
    subjectPublicKeyInfo,
  ];

  if (nonce) {
    // Extension ::= SEQUENCE { extnID, critical DEFAULT FALSE, extnValue OCTET STRING }, whose
    // content is SEQUENCE { [1] EXPLICIT OCTET STRING } as Apple specifies.
    parts.push(
      derTagged(
        3,
        derSequence(
          derSequence(
            derOid(APP_ATTEST_NONCE_OID),
            derOctetString(derSequence(derTagged(1, derOctetString(nonce))))
          )
        )
      )
    );
  }

  const tbsCertificate = derSequence(...parts);
  const signature = crypto.sign("sha256", tbsCertificate, issuerPrivateKey);

  return derSequence(
    tbsCertificate,
    derSequence(derOid(ECDSA_WITH_SHA256_OID)),
    derBitString(signature)
  );
};

const generateEcKeyPair = () => {
  const { publicKey, privateKey } = crypto.generateKeyPairSync("ec", {
    namedCurve: "prime256v1",
  });
  return {
    privateKey,
    subjectPublicKeyInfo: publicKey.export({ type: "spki", format: "der" }),
  };
};

/**
 * The root and the intermediate.
 *
 * Apple's `x5c` carries the credential certificate and the intermediate; the root is held by the
 * verifier, so it is returned separately for the client configuration rather than added to a chain.
 */
export const generateAttestationAuthority = () => {
  const root = generateEcKeyPair();
  const rootDer = certificate({
    subjectPublicKeyInfo: root.subjectPublicKeyInfo,
    subject: "test-app-attest-root",
    issuer: "test-app-attest-root",
    issuerPrivateKey: root.privateKey,
  });

  const intermediate = generateEcKeyPair();
  const intermediateDer = certificate({
    subjectPublicKeyInfo: intermediate.subjectPublicKeyInfo,
    subject: "test-app-attest-ca",
    issuer: "test-app-attest-root",
    issuerPrivateKey: root.privateKey,
  });

  return {
    root,
    rootDer,
    rootBase64: rootDer.toString("base64"),
    intermediate,
    intermediateDer,
  };
};

/** The key identifier Apple derives: SHA-256 of the key in X9.62 uncompressed point format. */
export const keyIdentifier = (publicJwk) =>
  sha256(
    Buffer.concat([
      Buffer.from([0x04]),
      Buffer.from(publicJwk.x, "base64url"),
      Buffer.from(publicJwk.y, "base64url"),
    ])
  );

/**
 * rpIdHash ‖ flags ‖ counter ‖ aaguid ‖ credentialIdLength ‖ credentialId ‖ encoded key.
 *
 * The flags byte sets AT, which is what a device sets when attested credential data follows. The
 * trailing 77 bytes stand in for the COSE encoded key: the verifier reads nothing past
 * credentialId, so this occupies the space the layout allocates without pretending to be COSE.
 */
const authenticatorData = ({ appId, counter, aaguid, credentialId }) => {
  const counterBytes = Buffer.alloc(4);
  counterBytes.writeUInt32BE(counter);

  const credentialIdLength = Buffer.alloc(2);
  credentialIdLength.writeUInt16BE(credentialId.length);

  return Buffer.concat([
    sha256(Buffer.from(appId, "utf8")),
    Buffer.from([0x40]),
    counterBytes,
    aaguid,
    credentialIdLength,
    credentialId,
    Buffer.alloc(77),
  ]);
};

/**
 * An attestation object certifying `publicKeyPem`, base64 encoded as platform_evidence carries it.
 *
 * The key is supplied by the caller rather than generated here, because the same key has to sign
 * the Client Attestation JWT afterwards: the registration proves the key came from the Secure
 * Enclave, and the authentication proves the client holds it.
 */
export const generateAttestation = ({
  authority,
  challenge,
  appId,
  publicKeyPem,
  publicJwk,
  environment = ENVIRONMENT.production,
  counter = 0,
  credentialId,
  format = "apple-appattest",
  signedByUntrustedRoot = false,
}) => {
  const authData = authenticatorData({
    appId,
    counter,
    aaguid: environment,
    credentialId: credentialId ?? keyIdentifier(publicJwk),
  });

  const nonce = sha256(
    Buffer.concat([authData, sha256(Buffer.from(challenge, "base64url"))])
  );

  const issuer = signedByUntrustedRoot
    ? generateAttestationAuthority()
    : authority;

  const credentialCertificate = certificate({
    subjectPublicKeyInfo: pemToDer(publicKeyPem),
    subject: "attested-key",
    issuer: "test-app-attest-ca",
    issuerPrivateKey: issuer.intermediate.privateKey,
    nonce,
  });

  const attestationObject = cbor.encode({
    fmt: format,
    attStmt: {
      x5c: [credentialCertificate, issuer.intermediateDer],
      receipt: Buffer.from([0x30, 0x00]),
    },
    authData,
  });

  return attestationObject.toString("base64");
};

export const platformEvidence = (attestationObject) => ({
  platform: PLATFORM,
  attestation_object: attestationObject,
});
