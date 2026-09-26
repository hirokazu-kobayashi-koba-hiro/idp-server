#!/usr/bin/env node
/**
 * Builds Apple App Attest evidence for Client Instance registration, the way a device's Secure
 * Enclave would build it.
 *
 * What a device produces and what this builds differ in one way only: the chain leads to a root
 * generated here rather than to Apple's, so the client has to trust that root through
 * client_instance_platform_config.ios_app_attest.override_root_certificates. The server verifies
 * the evidence exactly as it verifies a device's. Uses only Node built-ins.
 *
 *   # generate the test root and intermediate (kept in app-attest-authority.json), print the root
 *   node mint-app-attest.mjs --init-authority
 *
 *   # platform_evidence certifying the Client Instance Key (instance-key.json, as
 *   # mint-attestation.mjs keeps it) for a registration challenge
 *   node mint-app-attest.mjs --evidence --challenge <challenge> --app-id <TEAMID.bundle.id>
 *
 * @see https://developer.apple.com/documentation/devicecheck/validating-apps-that-connect-to-your-server
 */
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";

const args = process.argv.slice(2);
const opt = (name, fallback) => {
  const i = args.indexOf(`--${name}`);
  return i >= 0 ? args[i + 1] : fallback;
};
const flag = (name) => args.includes(`--${name}`);

const outDir = opt("out-dir", process.env.ABCA_OUT_DIR || ".");
const authorityPath = path.join(outDir, "app-attest-authority.json");
const instanceKeyPath = path.join(outDir, "instance-key.json");

const sha256 = (input) => crypto.createHash("sha256").update(input).digest();

// --- DER ---------------------------------------------------------------------------------------

const derLength = (size) => {
  if (size < 128) return Buffer.from([size]);
  const octets = [];
  for (let remaining = size; remaining > 0; remaining >>= 8) octets.unshift(remaining & 0xff);
  return Buffer.from([0x80 | octets.length, ...octets]);
};
const tlv = (tag, content) => Buffer.concat([Buffer.from([tag]), derLength(content.length), content]);
const sequence = (...parts) => tlv(0x30, Buffer.concat(parts));
const set = (...parts) => tlv(0x31, Buffer.concat(parts));
const tagged = (number, content) => tlv(0xa0 | number, content);
const octetString = (content) => tlv(0x04, Buffer.from(content));
const bitString = (content) => tlv(0x03, Buffer.concat([Buffer.from([0x00]), content]));
const boolean = (value) => tlv(0x01, Buffer.from([value ? 0xff : 0x00]));
const integer = (bytes) => {
  let value = Array.from(bytes);
  while (value.length > 1 && value[0] === 0 && (value[1] & 0x80) === 0) value.shift();
  if (value[0] & 0x80) value.unshift(0);
  return tlv(0x02, Buffer.from(value));
};
const oid = (dotted) => {
  const arcs = dotted.split(".").map(Number);
  const bytes = [arcs[0] * 40 + arcs[1]];
  for (const arc of arcs.slice(2)) {
    const groups = [];
    let remaining = arc;
    do {
      groups.unshift(remaining & 0x7f);
      remaining >>= 7;
    } while (remaining > 0);
    bytes.push(...groups.map((group, index) => (index === groups.length - 1 ? group : group | 0x80)));
  }
  return tlv(0x06, Buffer.from(bytes));
};
const utcTime = (date) => {
  const pad = (value) => String(value).padStart(2, "0");
  const text =
    pad(date.getUTCFullYear() % 100) +
    pad(date.getUTCMonth() + 1) +
    pad(date.getUTCDate()) +
    pad(date.getUTCHours()) +
    pad(date.getUTCMinutes()) +
    pad(date.getUTCSeconds()) +
    "Z";
  return tlv(0x17, Buffer.from(text, "ascii"));
};
const commonName = (value) =>
  sequence(set(sequence(oid("2.5.4.3"), tlv(0x0c, Buffer.from(value, "utf8")))));
const extension = (id, critical, value) =>
  critical ? sequence(oid(id), boolean(true), octetString(value)) : sequence(oid(id), octetString(value));

const ECDSA_WITH_SHA256 = "1.2.840.10045.4.3.2";
const APP_ATTEST_NONCE = "1.2.840.113635.100.8.2";

/**
 * An X.509 certificate. A CA (caPathLen given) carries BasicConstraints and KeyUsage keyCertSign,
 * which the server requires of every issuer in the chain; the credential certificate carries the
 * App Attest nonce extension instead.
 */
const certificate = ({ spki, subject, issuer, issuerKey, caPathLen, nonce }) => {
  const now = Date.now();
  const extensions = [];
  if (caPathLen !== undefined) {
    extensions.push(
      extension("2.5.29.19", true, sequence(boolean(true), integer([caPathLen]))),
      extension("2.5.29.15", true, tlv(0x03, Buffer.from([0x01, 0x06]))) // keyCertSign, cRLSign
    );
  }
  if (nonce) {
    // extnValue is SEQUENCE { [1] EXPLICIT OCTET STRING } as Apple specifies.
    extensions.push(extension(APP_ATTEST_NONCE, false, sequence(tagged(1, octetString(nonce)))));
  }
  const tbs = sequence(
    tagged(0, integer([2])),
    integer(crypto.randomBytes(8)),
    sequence(oid(ECDSA_WITH_SHA256)),
    commonName(issuer),
    sequence(utcTime(new Date(now - 3600e3)), utcTime(new Date(now + 86400e3))),
    commonName(subject),
    spki,
    tagged(3, sequence(...extensions))
  );
  const signature = crypto.sign("sha256", tbs, issuerKey);
  return sequence(tbs, sequence(oid(ECDSA_WITH_SHA256)), bitString(signature));
};

// --- CBOR (the subset an attestation object needs) ---------------------------------------------

const cborHead = (major, length) => {
  if (length < 24) return Buffer.from([(major << 5) | length]);
  if (length < 0x100) return Buffer.from([(major << 5) | 24, length]);
  if (length < 0x10000) return Buffer.from([(major << 5) | 25, length >> 8, length & 0xff]);
  const head = Buffer.alloc(5);
  head[0] = (major << 5) | 26;
  head.writeUInt32BE(length, 1);
  return head;
};
const cbor = (value) => {
  if (Buffer.isBuffer(value)) return Buffer.concat([cborHead(2, value.length), value]);
  if (typeof value === "string") {
    const text = Buffer.from(value, "utf8");
    return Buffer.concat([cborHead(3, text.length), text]);
  }
  if (Array.isArray(value)) return Buffer.concat([cborHead(4, value.length), ...value.map(cbor)]);
  const entries = Object.entries(value);
  return Buffer.concat([cborHead(5, entries.length), ...entries.flatMap(([k, v]) => [cbor(k), cbor(v)])]);
};

// --- commands ----------------------------------------------------------------------------------

const newEcKey = () => crypto.generateKeyPairSync("ec", { namedCurve: "prime256v1" });
const spkiOf = (publicKey) => publicKey.export({ type: "spki", format: "der" });

if (flag("init-authority")) {
  const root = newEcKey();
  const intermediate = newEcKey();
  const rootDer = certificate({
    spki: spkiOf(root.publicKey),
    subject: "test-app-attest-root",
    issuer: "test-app-attest-root",
    issuerKey: root.privateKey,
    caPathLen: 1,
  });
  const intermediateDer = certificate({
    spki: spkiOf(intermediate.publicKey),
    subject: "test-app-attest-ca",
    issuer: "test-app-attest-root",
    issuerKey: root.privateKey,
    caPathLen: 0,
  });
  fs.mkdirSync(outDir, { recursive: true });
  fs.writeFileSync(
    authorityPath,
    JSON.stringify(
      {
        root_certificate: rootDer.toString("base64"),
        intermediate_certificate: intermediateDer.toString("base64"),
        intermediate_private_key: intermediate.privateKey.export({ type: "pkcs8", format: "pem" }),
      },
      null,
      2
    ) + "\n"
  );
  process.stderr.write(`generated App Attest test authority: ${authorityPath}\n`);
  console.log(rootDer.toString("base64"));
  process.exit(0);
}

if (flag("evidence")) {
  const challenge = opt("challenge");
  const appId = opt("app-id");
  if (!challenge || !appId) {
    console.error("--challenge and --app-id are required with --evidence");
    process.exit(1);
  }
  if (!fs.existsSync(authorityPath) || !fs.existsSync(instanceKeyPath)) {
    console.error(`${authorityPath} and ${instanceKeyPath} are required (run --init-authority, and mint-attestation.mjs --print-jwk)`);
    process.exit(1);
  }
  const authority = JSON.parse(fs.readFileSync(authorityPath, "utf8"));
  const { kty, crv, x, y } = JSON.parse(fs.readFileSync(instanceKeyPath, "utf8")).public;
  const instancePublicKey = crypto.createPublicKey({ key: { kty, crv, x, y }, format: "jwk" });

  // The key identifier Apple derives: SHA-256 of the key as an uncompressed point.
  const keyId = sha256(Buffer.concat([Buffer.from([0x04]), Buffer.from(x, "base64url"), Buffer.from(y, "base64url")]));

  // rpIdHash | flags (AT) | counter 0 | aaguid "appattest" (production) | credentialId length | id | key
  const credentialIdLength = Buffer.alloc(2);
  credentialIdLength.writeUInt16BE(keyId.length);
  const authData = Buffer.concat([
    sha256(Buffer.from(appId, "utf8")),
    Buffer.from([0x40]),
    Buffer.alloc(4),
    Buffer.concat([Buffer.from("appattest", "ascii"), Buffer.alloc(7)]),
    credentialIdLength,
    keyId,
    Buffer.alloc(77),
  ]);

  // clientDataHash is SHA-256 of the challenge bytes, not of the base64url text.
  const nonce = sha256(Buffer.concat([authData, sha256(Buffer.from(challenge, "base64url"))]));

  const credentialCertificate = certificate({
    spki: spkiOf(instancePublicKey),
    subject: "attested-key",
    issuer: "test-app-attest-ca",
    issuerKey: crypto.createPrivateKey(authority.intermediate_private_key),
    nonce,
  });

  const attestationObject = cbor({
    fmt: "apple-appattest",
    attStmt: {
      x5c: [credentialCertificate, Buffer.from(authority.intermediate_certificate, "base64")],
      receipt: Buffer.from([0x30, 0x00]),
    },
    authData,
  });

  console.log(
    JSON.stringify({ platform: "ios-app-attest", attestation_object: attestationObject.toString("base64") })
  );
  process.exit(0);
}

console.error("specify --init-authority or --evidence");
process.exit(1);
