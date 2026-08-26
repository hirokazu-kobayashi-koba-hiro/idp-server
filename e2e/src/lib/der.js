/**
 * A minimal DER writer.
 *
 * Attestation evidence is DER that no JS library here can produce: node-forge writes its own
 * identifier for every ASN.1 node, which cannot express high tag numbers such as Android's [709]
 * and cannot carry pre-encoded bytes without prefixing them, and it signs certificates with RSA
 * only, while Apple App Attest keys are EC P-256. Both platforms end up assembling bytes directly,
 * so the primitives live here rather than in either one.
 */

/** Length in the definite form: short when it fits in 7 bits, long otherwise. */
const length = (size) => {
  if (size < 128) {
    return Buffer.from([size]);
  }
  const octets = [];
  let remaining = size;
  while (remaining > 0) {
    octets.unshift(remaining & 0xff);
    remaining >>= 8;
  }
  return Buffer.from([0x80 | octets.length, ...octets]);
};

export const tlv = (tag, content) =>
  Buffer.concat([Buffer.from([tag]), length(content.length), content]);

/** Base 128, most significant group first, every group but the last with the top bit set. */
const base128 = (value) => {
  const groups = [];
  let remaining = value;
  do {
    groups.unshift(remaining & 0x7f);
    remaining >>= 7;
  } while (remaining > 0);
  return groups.map((group, index) =>
    index === groups.length - 1 ? group : group | 0x80
  );
};

/** A signed INTEGER, minimally encoded, with a leading zero when the top bit would read negative. */
export const derInteger = (value) => {
  let bytes =
    typeof value === "bigint" || value > 0xff
      ? bigIntBytes(BigInt(value))
      : [value];
  bytes = Array.from(bytes);
  while (bytes.length > 1 && bytes[0] === 0 && (bytes[1] & 0x80) === 0) {
    bytes.shift();
  }
  if (bytes[0] & 0x80) {
    bytes.unshift(0);
  }
  return tlv(0x02, Buffer.from(bytes));
};

const bigIntBytes = (value) => {
  let hex = value.toString(16);
  if (hex.length % 2) {
    hex = `0${hex}`;
  }
  return Buffer.from(hex, "hex");
};

export const derEnumerated = (value) => tlv(0x0a, Buffer.from([value]));
export const derOctetString = (content) => tlv(0x04, Buffer.from(content));
export const derUtf8String = (text) => tlv(0x0c, Buffer.from(text, "utf8"));
export const derBoolean = (value) =>
  tlv(0x01, Buffer.from([value ? 0xff : 0x00]));
export const derNull = () => tlv(0x05, Buffer.alloc(0));
export const derSequence = (...parts) => tlv(0x30, Buffer.concat(parts));
export const derSet = (...parts) => tlv(0x31, Buffer.concat(parts));

/** A BIT STRING whose content is a whole number of bytes, so no bits are unused. */
export const derBitString = (content) =>
  tlv(0x03, Buffer.concat([Buffer.from([0x00]), Buffer.from(content)]));

/** The first two arcs share one byte as 40 * a + b, the rest are base 128. */
export const derOid = (dotted) => {
  const arcs = dotted.split(".").map(Number);
  const bytes = [arcs[0] * 40 + arcs[1]];
  arcs.slice(2).forEach((arc) => bytes.push(...base128(arc)));
  return tlv(0x06, Buffer.from(bytes));
};

/** YYMMDDHHMMSSZ, which is what X.509 uses for years before 2050. */
export const derUtcTime = (date) => {
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

/**
 * A context specific constructed tag.
 *
 * Tag numbers above 30 need the high tag number form: 0xBF (context | constructed | 31) followed
 * by the number in base 128. Android's attestationApplicationId is [709], which needs it.
 */
export const derTagged = (tagNumber, content) => {
  const identifier =
    tagNumber < 31 ? [0xa0 | tagNumber] : [0xbf, ...base128(tagNumber)];
  return Buffer.concat([
    Buffer.from(identifier),
    length(content.length),
    content,
  ]);
};
