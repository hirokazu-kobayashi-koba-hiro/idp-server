/**
 * WebAuthn binary <-> Base64URL helpers.
 *
 * WebAuthn transmits binary data (challenge, credential id, attestation) as Base64URL strings.
 * Extracted here so every step component shares one implementation (Safari needs manual
 * serialization of the credential response).
 */

/** Convert a Base64URL string to a Uint8Array. */
export const base64UrlToBuffer = (base64url: string): Uint8Array => {
  const base64 = base64url.replace(/-/g, "+").replace(/_/g, "/");
  const binaryString = atob(base64);
  return Uint8Array.from(binaryString, (char) => char.charCodeAt(0));
};

/** Convert an ArrayBuffer to a Base64URL string. */
export const bufferToBase64Url = (buffer: ArrayBuffer): string => {
  const bytes = new Uint8Array(buffer);
  let binary = "";
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  const base64 = btoa(binary);
  return base64.replace(/\+/g, "-").replace(/\//g, "_").replace(/=/g, "");
};
