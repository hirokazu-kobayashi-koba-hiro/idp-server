/**
 * Builds the certificate chain a Client Attester presents in the `x5c` JOSE header.
 *
 * One root issues any number of attester certificates, which is what lets a test rotate the
 * signing key under an unchanged root — the property the `x5c` trust source exists for. The root
 * carries CA constraints because the verifier requires them of anything it treats as an issuer.
 *
 * @see draft-ietf-oauth-attestation-based-client-auth-11 Section 10.8
 */
import forge from "node-forge";
import * as jose from "jose";

const certificate = ({ publicKey, signWith, subject, issuer, caPathLen }) => {
  const cert = forge.pki.createCertificate();
  cert.publicKey = publicKey;
  cert.serialNumber = `0${Math.floor(Math.random() * 900000) + 100000}`;
  cert.validity.notBefore = new Date(Date.now() - 3600 * 1000);
  cert.validity.notAfter = new Date(Date.now() + 24 * 3600 * 1000);
  cert.setSubject([{ name: "commonName", value: subject }]);
  cert.setIssuer([{ name: "commonName", value: issuer }]);

  if (caPathLen !== undefined) {
    cert.setExtensions([
      {
        name: "basicConstraints",
        critical: true,
        cA: true,
        pathLenConstraint: caPathLen,
      },
      { name: "keyUsage", critical: true, keyCertSign: true, cRLSign: true },
    ]);
  }

  cert.sign(signWith, forge.md.sha256.create());
  return cert;
};

const toBase64Der = (cert) =>
  Buffer.from(
    forge.asn1.toDer(forge.pki.certificateToAsn1(cert)).getBytes(),
    "binary"
  ).toString("base64");

/** A self-signed root, the value a deployment pins in `client_attestation_trusted_root_certificates`. */
export const generateAttesterRoot = () => {
  const keys = forge.pki.rsa.generateKeyPair(2048);
  const cert = certificate({
    publicKey: keys.publicKey,
    signWith: keys.privateKey,
    subject: "attester-root",
    issuer: "attester-root",
    caPathLen: 1,
  });
  return { keys, certificate: cert, base64Der: toBase64Der(cert) };
};

/**
 * An attester certificate issued by `root`, with the key that signs the Client Attestation JWT.
 *
 * RS256 rather than ES256 because forge builds certificates from RSA keys, and the same key has to
 * be usable by `jose` to sign the JWT.
 *
 * @returns privateJwk for signing, and both chain shapes: HAIP requires the trust anchor to be
 *   excluded, while an attester serialising its whole chain includes it, so both occur.
 */
export const issueAttesterCertificate = async ({ root, name = "attester" }) => {
  const { publicKey, privateKey } = await jose.generateKeyPair("RS256", {
    extractable: true,
  });
  const privateJwk = await jose.exportJWK(privateKey);
  const publicKeyPem = await jose.exportSPKI(publicKey);

  const cert = certificate({
    publicKey: forge.pki.publicKeyFromPem(publicKeyPem),
    signWith: root.keys.privateKey,
    subject: name,
    issuer: "attester-root",
  });

  const leafBase64 = toBase64Der(cert);
  return {
    privateJwk,
    x5c: [leafBase64, root.base64Der],
    x5cWithoutRoot: [leafBase64],
  };
};
