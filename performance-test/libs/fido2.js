// crypto is globally available in k6 as standard WebCrypto API
import encoding from "k6/encoding";

// ==========================================
// CBOR Encoding (Simplified for FIDO2)
// ==========================================

/**
 * Simplified CBOR encoder for FIDO2 WebAuthn
 * Only implements the subset needed for FIDO2 attestation/assertion
 */

const CBOR_MAJOR_TYPE = {
    UNSIGNED_INT: 0,
    NEGATIVE_INT: 1,
    BYTE_STRING: 2,
    TEXT_STRING: 3,
    ARRAY: 4,
    MAP: 5,
    TAG: 6,
    SIMPLE_FLOAT: 7,
};

const encodeCBORValue = (value) => {
    if (value instanceof Uint8Array || value instanceof ArrayBuffer) {
        // Byte string
        const bytes = value instanceof ArrayBuffer ? new Uint8Array(value) : value;
        return encodeCBORByteString(bytes);
    } else if (typeof value === "number" && Number.isInteger(value)) {
        // Integer
        return encodeCBORInteger(value);
    } else if (typeof value === "string") {
        // Text string
        return encodeCBORTextString(value);
    } else if (value instanceof Map) {
        // Map
        return encodeCBORMap(value);
    } else if (Array.isArray(value)) {
        // Array
        return encodeCBORArray(value);
    } else if (typeof value === "object" && value !== null) {
        // Object as Map
        return encodeCBORObjectAsMap(value);
    }
    throw new Error(`Unsupported CBOR value type: ${typeof value}`);
};

const encodeCBORInteger = (value) => {
    if (value >= 0) {
        return encodeCBORUnsignedInt(value);
    } else {
        return encodeCBORNegativeInt(value);
    }
};

const encodeCBORUnsignedInt = (value) => {
    const majorType = CBOR_MAJOR_TYPE.UNSIGNED_INT << 5;

    if (value < 24) {
        return new Uint8Array([majorType | value]);
    } else if (value < 256) {
        return new Uint8Array([majorType | 24, value]);
    } else if (value < 65536) {
        return new Uint8Array([majorType | 25, (value >> 8) & 0xff, value & 0xff]);
    } else if (value < 4294967296) {
        return new Uint8Array([
            majorType | 26,
            (value >> 24) & 0xff,
            (value >> 16) & 0xff,
            (value >> 8) & 0xff,
            value & 0xff,
        ]);
    }
    throw new Error("Integer too large for CBOR encoding");
};

const encodeCBORNegativeInt = (value) => {
    const majorType = CBOR_MAJOR_TYPE.NEGATIVE_INT << 5;
    const encoded = -1 - value;

    if (encoded < 24) {
        return new Uint8Array([majorType | encoded]);
    } else if (encoded < 256) {
        return new Uint8Array([majorType | 24, encoded]);
    } else if (encoded < 65536) {
        return new Uint8Array([majorType | 25, (encoded >> 8) & 0xff, encoded & 0xff]);
    }
    throw new Error("Negative integer too large for CBOR encoding");
};

const encodeCBORByteString = (bytes) => {
    const majorType = CBOR_MAJOR_TYPE.BYTE_STRING << 5;
    const length = bytes.length;
    const header = encodeLengthHeader(majorType, length);
    return new Uint8Array([...header, ...bytes]);
};

const encodeCBORTextString = (text) => {
    const majorType = CBOR_MAJOR_TYPE.TEXT_STRING << 5;
    const bytes = stringToUint8Array(text);
    const header = encodeLengthHeader(majorType, bytes.length);
    return new Uint8Array([...header, ...bytes]);
};

const encodeCBORArray = (array) => {
    const majorType = CBOR_MAJOR_TYPE.ARRAY << 5;
    const header = encodeLengthHeader(majorType, array.length);
    const encodedItems = array.map(item => encodeCBORValue(item));
    return new Uint8Array([
        ...header,
        ...encodedItems.flat(),
    ]);
};

const encodeCBORMap = (map) => {
    const majorType = CBOR_MAJOR_TYPE.MAP << 5;
    const header = encodeLengthHeader(majorType, map.size);
    const entries = [];

    for (const [key, value] of map.entries()) {
        entries.push(...encodeCBORValue(key));
        entries.push(...encodeCBORValue(value));
    }

    return new Uint8Array([...header, ...entries]);
};

const encodeCBORObjectAsMap = (obj) => {
    const map = new Map(Object.entries(obj));
    return encodeCBORMap(map);
};

const encodeLengthHeader = (majorType, length) => {
    if (length < 24) {
        return new Uint8Array([majorType | length]);
    } else if (length < 256) {
        return new Uint8Array([majorType | 24, length]);
    } else if (length < 65536) {
        return new Uint8Array([majorType | 25, (length >> 8) & 0xff, length & 0xff]);
    } else if (length < 4294967296) {
        return new Uint8Array([
            majorType | 26,
            (length >> 24) & 0xff,
            (length >> 16) & 0xff,
            (length >> 8) & 0xff,
            length & 0xff,
        ]);
    }
    throw new Error("Length too large for CBOR encoding");
};

// ==========================================
// Utilities
// ==========================================

const bufferToBase64Url = (buffer) => {
    const bytes = buffer instanceof Uint8Array ? buffer : new Uint8Array(buffer);
    // Use k6 encoding module for base64
    const base64 = encoding.b64encode(bytes.buffer, "rawstd");
    return base64
        .replace(/\+/g, "-")
        .replace(/\//g, "_")
        .replace(/=/g, "");
};

const stringToUint8Array = (str) => {
    // Manual UTF-8 encoding for k6 (no TextEncoder)
    const utf8 = [];
    for (let i = 0; i < str.length; i++) {
        let charCode = str.charCodeAt(i);
        if (charCode < 0x80) {
            utf8.push(charCode);
        } else if (charCode < 0x800) {
            utf8.push(0xc0 | (charCode >> 6), 0x80 | (charCode & 0x3f));
        } else if (charCode < 0xd800 || charCode >= 0xe000) {
            utf8.push(0xe0 | (charCode >> 12), 0x80 | ((charCode >> 6) & 0x3f), 0x80 | (charCode & 0x3f));
        } else {
            // Surrogate pair
            i++;
            charCode = 0x10000 + (((charCode & 0x3ff) << 10) | (str.charCodeAt(i) & 0x3ff));
            utf8.push(0xf0 | (charCode >> 18), 0x80 | ((charCode >> 12) & 0x3f), 0x80 | ((charCode >> 6) & 0x3f), 0x80 | (charCode & 0x3f));
        }
    }
    return new Uint8Array(utf8);
};

const convertEcdsaDerToRaw = (derSignature) => {
    // DER signature to raw format (r || s)
    // This is the inverse of what we need - keep DER format
    return derSignature;
};

// Store key pairs for authentication (credential ID -> key pair)
const keyPairs = new Map();

// ==========================================
// FIDO2 Core Functions
// ==========================================

/**
 * Generate AuthenticatorData for registration
 *
 * Format:
 * - rpIdHash (32 bytes)
 * - flags (1 byte)
 * - signCount (4 bytes, big-endian)
 * - attestedCredentialData:
 *   - aaguid (16 bytes)
 *   - credentialIdLength (2 bytes, big-endian)
 *   - credentialId (variable)
 *   - credentialPublicKey (COSE format, variable)
 */
const generateAuthenticatorDataForRegistration = async (
    rpId,
    flags,
    signCount,
    aaguid,
    credentialId,
    publicKeyCose
) => {
    // rpIdHash: SHA-256 of rpId
    const rpIdBytes = stringToUint8Array(rpId);
    const rpIdHashBuffer = await crypto.subtle.digest("SHA-256", rpIdBytes);
    const rpIdHash = new Uint8Array(rpIdHashBuffer);

    // Flags: UP=1, UV=1, AT=1, ED=0 = 0x45
    const flagsByte = new Uint8Array([flags]);

    // Sign count (big-endian 4 bytes)
    const signCountBytes = new Uint8Array(4);
    const dataView = new DataView(signCountBytes.buffer);
    dataView.setUint32(0, signCount, false); // big-endian

    // AAGUID (16 bytes)
    const aaguidBytes = new Uint8Array(aaguid);

    // Credential ID length (big-endian 2 bytes)
    const credentialIdLengthBytes = new Uint8Array(2);
    const lengthView = new DataView(credentialIdLengthBytes.buffer);
    lengthView.setUint16(0, credentialId.length, false); // big-endian

    // Concatenate all
    return new Uint8Array([
        ...rpIdHash,
        ...flagsByte,
        ...signCountBytes,
        ...aaguidBytes,
        ...credentialIdLengthBytes,
        ...credentialId,
        ...publicKeyCose,
    ]);
};

/**
 * Generate AuthenticatorData for authentication
 *
 * Format:
 * - rpIdHash (32 bytes)
 * - flags (1 byte)
 * - signCount (4 bytes, big-endian)
 */
const generateAuthenticatorDataForAuthentication = async (
    rpId,
    flags,
    signCount
) => {
    // rpIdHash: SHA-256 of rpId
    const rpIdBytes = stringToUint8Array(rpId);
    const rpIdHashBuffer = await crypto.subtle.digest("SHA-256", rpIdBytes);
    const rpIdHash = new Uint8Array(rpIdHashBuffer);

    // Flags: UP=1, UV=1, AT=0, ED=0 = 0x05
    const flagsByte = new Uint8Array([flags]);

    // Sign count (big-endian 4 bytes)
    const signCountBytes = new Uint8Array(4);
    const dataView = new DataView(signCountBytes.buffer);
    dataView.setUint32(0, signCount, false); // big-endian

    return new Uint8Array([
        ...rpIdHash,
        ...flagsByte,
        ...signCountBytes,
    ]);
};

/**
 * Generate COSE public key (ES256)
 *
 * COSE Key format (Map):
 * {
 *   1: 2,        // kty: EC2
 *   3: -7,       // alg: ES256
 *   -1: 1,       // crv: P-256
 *   -2: x,       // x coordinate (32 bytes)
 *   -3: y        // y coordinate (32 bytes)
 * }
 */
const generateCosePublicKey = async (publicKey) => {
    // Export public key as raw format
    const rawKey = await crypto.subtle.exportKey("raw", publicKey);
    const keyBytes = new Uint8Array(rawKey);

    // Raw format: 0x04 || x (32 bytes) || y (32 bytes)
    if (keyBytes[0] !== 0x04) {
        throw new Error("Invalid public key format");
    }

    const x = keyBytes.slice(1, 33);
    const y = keyBytes.slice(33, 65);

    // Create COSE key map
    const coseKey = new Map();
    coseKey.set(1, 2);           // kty: EC2
    coseKey.set(3, -7);          // alg: ES256
    coseKey.set(-1, 1);          // crv: P-256
    coseKey.set(-2, x);          // x coordinate
    coseKey.set(-3, y);          // y coordinate

    return encodeCBORValue(coseKey);
};

/**
 * Generate ClientDataJSON
 */
const generateClientDataJSON = (type, challenge, origin) => {
    const clientData = {
        type,
        challenge,
        origin,
        crossOrigin: false,
    };
    return JSON.stringify(clientData);
};

/**
 * Generate ES256 signature (DER format)
 * Reuses FIDO UAF's signature generation logic
 */
const generateSignature = async (privateKey, data) => {
    const signature = await crypto.subtle.sign(
        { name: "ECDSA", hash: { name: "SHA-256" } },
        privateKey,
        data
    );

    // Convert raw signature (r || s) to DER format
    const rawSig = new Uint8Array(signature);
    return convertEcdsaRawToDer(rawSig);
};

/**
 * Convert ECDSA raw signature (r || s) to DER format
 * Same as FIDO UAF's convertEcdsaAnsi
 */
const convertEcdsaRawToDer = (rawSignature) => {
    const r = rawSignature.slice(0, 32);
    const s = rawSignature.slice(32, 64);

    // Remove leading zeros but keep at least one byte
    const trimR = trimLeadingZeros(r);
    const trimS = trimLeadingZeros(s);

    // Add 0x00 prefix if high bit is set (to indicate positive number)
    const rBytes = (trimR[0] & 0x80) ? new Uint8Array([0x00, ...trimR]) : trimR;
    const sBytes = (trimS[0] & 0x80) ? new Uint8Array([0x00, ...trimS]) : trimS;

    // Build DER: 0x30 [len] 0x02 [rLen] [r] 0x02 [sLen] [s]
    const innerSequence = new Uint8Array([
        0x02, rBytes.length, ...rBytes,
        0x02, sBytes.length, ...sBytes,
    ]);

    return new Uint8Array([
        0x30,
        innerSequence.length,
        ...innerSequence,
    ]);
};

const trimLeadingZeros = (bytes) => {
    let start = 0;
    while (start < bytes.length - 1 && bytes[start] === 0) {
        start++;
    }
    return bytes.slice(start);
};

// ==========================================
// Main API Functions
// ==========================================

/**
 * Generate valid credential from challenge response (Registration)
 *
 * @param {Object} challengeResponse - FIDO2 registration challenge response
 * @param {Object} options - Optional parameters (aaguid, attestation, rpId, origin)
 * @returns {Object} - WebAuthn credential
 */
export const generateValidCredentialFromChallenge = async (challengeResponse, options = {}) => {
    // Generate ES256 key pair
    const keyPair = await crypto.subtle.generateKey(
        {
            name: "ECDSA",
            namedCurve: "P-256",
        },
        true,
        ["sign", "verify"]
    );

    // Generate credential ID (32 random bytes)
    const credentialIdBytes = new Uint8Array(32);
    crypto.getRandomValues(credentialIdBytes);
    const credentialId = bufferToBase64Url(credentialIdBytes);

    // Store key pair for later authentication
    keyPairs.set(credentialId, keyPair);

    // AAGUID (16 bytes) - default all zeros
    let aaguid;
    if (options.aaguid) {
        const aaguidHex = options.aaguid.replace(/-/g, "");
        if (aaguidHex.length !== 32) {
            throw new Error("AAGUID must be 32 hex characters (16 bytes)");
        }
        aaguid = hexToBytes(aaguidHex);
    } else {
        aaguid = new Uint8Array(16); // All zeros
    }

    // Generate COSE public key
    const publicKeyCose = await generateCosePublicKey(keyPair.publicKey);

    // Generate AuthenticatorData
    const rpId = options.rpId || "localhost";
    const flags = 0x45; // UP=1, UV=1, AT=1
    const signCount = 0;

    const authenticatorData = await generateAuthenticatorDataForRegistration(
        rpId,
        flags,
        signCount,
        aaguid,
        credentialIdBytes,
        publicKeyCose
    );

    // Generate ClientDataJSON
    const origin = options.origin || "http://localhost:3000";
    const clientDataJSON = generateClientDataJSON(
        "webauthn.create",
        challengeResponse.challenge,
        origin
    );

    // Hash ClientDataJSON
    const clientDataBytes = stringToUint8Array(clientDataJSON);
    const clientDataHashBuffer = await crypto.subtle.digest("SHA-256", clientDataBytes);

    // Create Attestation Object (CBOR)
    const attestationMode = options.attestation || "none";
    const attestationObject = new Map();
    attestationObject.set("fmt", "none");
    attestationObject.set("attStmt", new Map()); // Empty map for "none" attestation
    attestationObject.set("authData", authenticatorData);

    const attestationObjectCbor = encodeCBORValue(attestationObject);

    // Export private key as JWK for storage
    let privateKeyJwk = null;
    try {
        privateKeyJwk = await crypto.subtle.exportKey("jwk", keyPair.privateKey);
    } catch (e) {
        // If exportKey fails, we can only use the key in-memory
        console.log("Warning: Could not export private key to JWK");
    }

    return {
        id: credentialId,
        rawId: credentialId,
        type: "public-key",
        response: {
            attestationObject: bufferToBase64Url(attestationObjectCbor),
            clientDataJSON: bufferToBase64Url(clientDataBytes),
        },
        // Additional fields for persistence
        privateKeyJwk: privateKeyJwk,
    };
};

/**
 * Generate valid assertion from challenge response (Authentication)
 *
 * @param {Object} challengeResponse - FIDO2 authentication challenge response
 * @param {string} credentialId - Credential ID to use for authentication
 * @param {Object} options - Optional parameters (rpId, origin, privateKeyJwk, signCount)
 * @returns {Object} - WebAuthn assertion
 */
export const generateValidAssertionFromChallenge = async (challengeResponse, credentialId, options = {}) => {
    let privateKey;

    // Try to get key from options (from SQLite) or from memory
    if (options.privateKeyJwk) {
        // Import private key from JWK
        privateKey = await crypto.subtle.importKey(
            "jwk",
            options.privateKeyJwk,
            { name: "ECDSA", namedCurve: "P-256" },
            false,
            ["sign"]
        );
    } else {
        // Retrieve stored key pair from memory
        const keyPair = keyPairs.get(credentialId);
        if (!keyPair) {
            throw new Error(`KeyPair not found for credential ID: ${credentialId}`);
        }
        privateKey = keyPair.privateKey;
    }

    // Generate AuthenticatorData (authentication)
    const rpId = options.rpId || "localhost";
    const flags = 0x05; // UP=1, UV=1
    const signCount = options.signCount || 1; // Use provided signCount or default to 1

    const authenticatorData = await generateAuthenticatorDataForAuthentication(
        rpId,
        flags,
        signCount
    );

    // Generate ClientDataJSON
    const origin = options.origin || "http://localhost:3000";
    const clientDataJSON = generateClientDataJSON(
        "webauthn.get",
        challengeResponse.challenge,
        origin
    );

    // Hash ClientDataJSON
    const clientDataBytes = stringToUint8Array(clientDataJSON);
    const clientDataHashBuffer = await crypto.subtle.digest("SHA-256", clientDataBytes);
    const clientDataHash = new Uint8Array(clientDataHashBuffer);

    // Create signature base (AuthenticatorData || ClientDataHash)
    const signatureBase = new Uint8Array([
        ...authenticatorData,
        ...clientDataHash,
    ]);

    // Generate signature
    const signature = await generateSignature(privateKey, signatureBase);

    // UserHandle (optional)
    const userHandle = challengeResponse.user ? challengeResponse.user.id : null;

    return {
        id: credentialId,
        rawId: credentialId,
        type: "public-key",
        response: {
            authenticatorData: bufferToBase64Url(authenticatorData),
            clientDataJSON: bufferToBase64Url(clientDataBytes),
            signature: bufferToBase64Url(signature),
            ...(userHandle && { userHandle }),
        },
    };
};

/**
 * Helper: Convert hex string to bytes
 */
const hexToBytes = (hex) => {
    const bytes = new Uint8Array(hex.length / 2);
    for (let i = 0; i < hex.length; i += 2) {
        bytes[i / 2] = parseInt(hex.substr(i, 2), 16);
    }
    return bytes;
};

// ==========================================
// Test/Mock Data (for compatibility)
// ==========================================

export const MOCK_USER_DATA = {
    STANDARD_USER: {
        username: "test@example.com",
        displayName: "test.user",
    },
    DISCOVERABLE_USER: {
        username: "discoverable@example.com",
        displayName: "discoverable.user",
    },
};

export const createRegistrationRequest = (overrides = {}) => {
    return {
        username: MOCK_USER_DATA.STANDARD_USER.username,
        displayName: MOCK_USER_DATA.STANDARD_USER.displayName,
        authenticatorSelection: {
            authenticatorAttachment: "platform",
            requireResidentKey: true,
            userVerification: "required",
        },
        attestation: "none",
        ...overrides,
    };
};

export const createAuthenticationRequest = (overrides = {}) => {
    return {
        username: MOCK_USER_DATA.STANDARD_USER.username,
        userVerification: "required",
        timeout: 60000,
        ...overrides,
    };
};
