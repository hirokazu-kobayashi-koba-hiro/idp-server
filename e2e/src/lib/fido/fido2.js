import { faker } from "@faker-js/faker";
import * as cbor from "cbor";
import * as crypto from "crypto";
import { ec as EC } from "elliptic";

// ランダムなテストユーザーを生成する関数
export function generateRandomUser() {
  const firstName = faker.person.firstName();
  const lastName = faker.person.lastName();
  const domain = faker.internet.domainName();

  return {
    username: faker.internet.email({ firstName, lastName, provider: domain }),
    displayName: `${firstName.toLowerCase()}.${lastName.toLowerCase()}`,
    firstName,
    lastName,
  };
}

// ランダムなCredential IDを生成する関数
export function generateRandomCredentialId() {
  return faker.string.alphanumeric(20);
}

// ランダムなチャレンジを生成する関数（Base64URL形式）
export function generateRandomChallenge() {
  return faker.string.alphanumeric(32);
}

// ランダムなタイムアウト値を生成する関数（30秒〜300秒）
export function generateRandomTimeout() {
  return faker.number.int({ min: 30000, max: 300000 });
}

// テストケース用のランダムなモックCredentialを生成する関数
export function generateRandomMockCredential() {
  return {
    id: faker.string.alphanumeric(20),
    rawId: faker.string.alphanumeric(20),
    type: "public-key",
    response: {
      attestationObject: faker.string.alphanumeric(50),
      clientDataJSON: faker.string.alphanumeric(50),
    },
  };
}

// キーペアを格納するグローバル変数
const keyPairs = new Map();

// 実際の公開鍵・秘密鍵ペアを生成してCredentialを作成する関数
export function generateValidCredentialFromChallenge(challengeResponse, options = {}) {
  // ES256用のP-256楕円曲線を使用
  const ec = new EC("p256");
  const keyPair = ec.genKeyPair();

  // CredentialIDを生成（バイナリからbase64url文字列へ）
  const credentialIdBytes = crypto.randomBytes(32);
  const credentialId = credentialIdBytes.toString("base64url");

  // キーペアを保存（後で認証時に使用）
  keyPairs.set(credentialId, keyPair);

  // RPIDのSHA256ハッシュを計算（originのホスト部分を使用）
  const rpId = "localhost";
  const rpIdHash = crypto.createHash("sha256").update(rpId).digest();

  // 公開鍵をCOSE形式で生成
  const publicKeyPoint = keyPair.getPublic();
  const x = publicKeyPoint.getX().toArrayLike(Buffer, "be", 32);
  const y = publicKeyPoint.getY().toArrayLike(Buffer, "be", 32);

  // COSE公開鍵をマップ形式で作成（CBORライブラリ用）
  const coseKeyMap = new Map();
  coseKeyMap.set(1, 2);        // kty: EC2
  coseKeyMap.set(3, -7);       // alg: ES256
  coseKeyMap.set(-1, 1);       // crv: P-256
  coseKeyMap.set(-2, x);       // x coordinate
  coseKeyMap.set(-3, y);       // y coordinate

  const cosePublicKey = cbor.encode(coseKeyMap);

  // AuthenticatorDataを構築
  const flags = Buffer.from([0x45]); // UP=1, UV=1, AT=1, ED=0 (User Verification required)
  const signCount = Buffer.alloc(4, 0); // サインカウンタ（初期値0）

  // AAGUIDの設定（オプションで指定可能、デフォルトは全ゼロ）
  let aaguid;
  if (options.aaguid) {
    // AAGUIDが指定された場合は16進数文字列として解釈
    const aaguidHex = options.aaguid.replace(/-/g, ""); // ハイフンを除去
    if (aaguidHex.length !== 32) {
      throw new Error("AAGUID must be 32 hex characters (16 bytes)");
    }
    aaguid = Buffer.from(aaguidHex, "hex");
  } else {
    aaguid = Buffer.alloc(16, 0x00); // デフォルト: 00000000-0000-0000-0000-000000000000
  }
  const credentialIdBuffer = credentialIdBytes; // 生のバイナリデータを使用
  const credentialIdLength = Buffer.alloc(2);
  credentialIdLength.writeUInt16BE(credentialIdBuffer.length, 0);

  const authenticatorData = Buffer.concat([
    rpIdHash,
    flags,
    signCount,
    aaguid,
    credentialIdLength,
    credentialIdBuffer,
    cosePublicKey
  ]);

  // ClientDataJSONを生成（データベースに設定されたRP_ORIGINを使用）
  const clientData = {
    type: "webauthn.create",
    challenge: challengeResponse.challenge,
    origin: "http://localhost:3000",
    crossOrigin: false
  };
  const clientDataJSON = JSON.stringify(clientData);
  const clientDataHash = crypto.createHash("sha256").update(clientDataJSON).digest();

  // AttestationObjectを生成 - CBORマップを使用
  const attestationObjectMap = new Map();
  const attestationMode = options.attestation || "none";

  if (attestationMode === "direct") {
    // Direct attestationの場合、packed形式でAAGUIDを含む簡易的なAttestationを生成
    attestationObjectMap.set("fmt", "packed");
    const attStmt = new Map();
    attStmt.set("alg", -7); // ES256
    attStmt.set("sig", crypto.randomBytes(64)); // ダミー署名
    attestationObjectMap.set("attStmt", attStmt);
  } else {
    // none attestationの場合
    attestationObjectMap.set("fmt", "none");
    attestationObjectMap.set("attStmt", new Map()); // 空のマップ
  }

  attestationObjectMap.set("authData", authenticatorData);
  const attestationObject = cbor.encode(attestationObjectMap).toString("base64url");

  return {
    id: credentialId,
    rawId: credentialId, // Both id and rawId are the same base64url string for JSON transport
    type: "public-key",
    response: {
      attestationObject,
      clientDataJSON: Buffer.from(clientDataJSON).toString("base64url")
    }
  };
}

// 認証チャレンジのレスポンスから有効なAssertionを生成する関数
export function generateValidAssertionFromChallenge(challengeResponse, credentialId) {
  // 保存されたキーペアを取得
  const keyPair = keyPairs.get(credentialId);
  if (!keyPair) {
    throw new Error(`KeyPair not found for credential ID: ${credentialId}`);
  }

  // RPIDのSHA256ハッシュを計算
  const rpId = "localhost";
  const rpIdHash = crypto.createHash("sha256").update(rpId).digest();

  // AuthenticatorData（認証用）
  const flags = Buffer.from([0x05]); // UP=1, UV=1, AT=0, ED=0
  const signCount = Buffer.from([0x00, 0x00, 0x00, 0x01]); // サインカウンタ（増加）

  const authenticatorData = Buffer.concat([
    rpIdHash,
    flags,
    signCount
  ]);

  // ClientDataJSONを生成（認証用）
  const clientData = {
    type: "webauthn.get",
    challenge: challengeResponse.challenge,
    origin: "http://localhost:3000",
    crossOrigin: false
  };
  const clientDataJSON = JSON.stringify(clientData);
  const clientDataHash = crypto.createHash("sha256").update(clientDataJSON).digest();

  // 署名対象データを作成（AuthenticatorData + ClientDataHash）
  const signatureBase = Buffer.concat([authenticatorData, clientDataHash]);

  // ES256署名を生成
  const signatureHash = crypto.createHash("sha256").update(signatureBase).digest();
  const signatureObj = keyPair.sign(signatureHash);

  // DER形式でエンコード
  const signature = Buffer.concat([
    Buffer.from(signatureObj.toDER())
  ]);

  // UserHandle（認証時に返される場合 - challenge responseのuser.idを使用）
  const userHandle = challengeResponse.user ? challengeResponse.user.id : null;

  return {
    id: credentialId,
    rawId: credentialId,
    type: "public-key",
    response: {
      authenticatorData: authenticatorData.toString("base64url"),
      clientDataJSON: Buffer.from(clientDataJSON).toString("base64url"),
      signature: signature.toString("base64url"),
      ...(userHandle && { userHandle })  // userHandleがある場合のみ追加
    }
  };
}

// 固定のテストユーザー（後方互換性のため）
export const MOCK_USER_DATA = {
  STANDARD_USER: {
    username: "test@example.com",
    displayName: "test.user",
  },
  DISCOVERABLE_USER: {
    username: "discoverable@example.com",
    displayName: "discoverable.user",
  },
  INTEGRATION_USER: {
    username: "integration@example.com",
    displayName: "integration.user",
  },
};

export const MOCK_CREDENTIALS = {
  REGISTRATION: {
    id: "dGVzdF9jcmVkZW50aWFsX2lk",
    rawId: "dGVzdF9jcmVkZW50aWFsX2lk",
    type: "public-key",
    response: {
      attestationObject: "o2NmbXRkbm9uZWdhdHRTdG10oGhhdXRoRGF0YVhFSxqf",
      clientDataJSON: "eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIiwiY2hhbGxlbmdl",
    },
  },
  AUTHENTICATION: {
    id: "dGVzdF9jcmVkZW50aWFsX2lk",
    rawId: "dGVzdF9jcmVkZW50aWFsX2lk",
    type: "public-key",
    response: {
      authenticatorData: "SZYN5YgOjGh0NBcPZHZgW4_krrmihjLHmVzzuoMdl2MFAAAAAQ",
      clientDataJSON: "eyJ0eXBlIjoid2ViYXV0aG4uZ2V0IiwiY2hhbGxlbmdl",
      signature: "MEYCIQDm7Q7kFnKhE8YZBmVNOOIvU",
      userHandle: "dGVzdF91c2VyX2hhbmRsZQ",
    },
  },
};

export const ERROR_CODES = {
  INVALID_PARAMS: "E40001",
  USER_NOT_FOUND: "E40003",
  VERIFICATION_FAILED: "E40005",
  CHALLENGE_EXPIRED: "E40006",
  AUTH_FAILED: "E40101",
  SIGNATURE_VERIFICATION_FAILED: "E40102",
  AUTHENTICATOR_NOT_ALLOWED: "E40171",
  INVALID_ATTESTATION: "E40172",
  INTERNAL_SERVER_ERROR: "E50001",
};

export function createRegistrationRequest(overrides = {}) {
  return {
    username: MOCK_USER_DATA.STANDARD_USER.username,
    displayName: MOCK_USER_DATA.STANDARD_USER.displayName,
    authenticatorSelection: {
      authenticatorAttachment: "platform",
      userVerification: "required",
    },
    attestation: "none",
    ...overrides,
  };
}

export function createDiscoverableRegistrationRequest(overrides = {}) {
  return {
    username: MOCK_USER_DATA.DISCOVERABLE_USER.username,
    displayName: MOCK_USER_DATA.DISCOVERABLE_USER.displayName,
    authenticatorSelection: {
      authenticatorAttachment: "platform",
      requireResidentKey: true,
      userVerification: "required",
    },
    attestation: "none",
    extensions: {
      credProps: true,
    },
    ...overrides,
  };
}

export function createAuthenticationRequest(overrides = {}) {
  return {
    username: MOCK_USER_DATA.STANDARD_USER.username,
    userVerification: "required",
    timeout: 60000,
    ...overrides,
  };
}

export function createCredentialDeletionRequest(ids = [], usernames = []) {
  return {
    ids,
    usernames,
  };
}
