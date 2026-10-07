/**
 * Helpers for the attest_jwt_client_auth scenarios (scenario-15 / 16 / 17).
 *
 * サーバは受け付けた PoP JWT の jti を記録し、同じ JWT の 2 回目を拒否する（#1893）。
 * PoP JWT の用意の仕方は POP_SIGNING で選ぶ。
 *
 *   presigned（既定）  スクリプトで前もってリクエストの数だけ署名した PoP JWT を、1 リクエストに 1 つずつ使う。
 *                      k6 側の署名コストが測定に混ざらない。テストは署名から窓（600 秒）の内側に収める。
 *   k6                 k6 の WebCrypto でリクエストごとに署名する。窓・件数の制限が無いので長時間のテストに使う。
 *                      k6 側で 1 リクエストあたり約 0.15ms の CPU を使う（5 VU では測定値に差が出なかった）。
 */

import exec from 'k6/execution';
import encoding from 'k6/encoding';

/** How PoP JWTs are made: `presigned` (default) or `k6`. */
export const POP_SIGNING = __ENV.POP_SIGNING || 'presigned';

if (POP_SIGNING !== 'presigned' && POP_SIGNING !== 'k6') {
  throw new Error(`Unsupported POP_SIGNING: ${POP_SIGNING} (presigned | k6)`);
}

/** Margin left between the end of a test and the end of the PoP JWT iat window. */
const MARGIN_SECONDS = 30;

/** Seconds of a k6 duration such as `30s`, `1m`, `1m30s` or `2h`. */
export function durationSeconds(duration) {
  const pattern = /(\d+(?:\.\d+)?)(ms|h|m|s)/g;
  const unitSeconds = { h: 3600, m: 60, s: 1, ms: 0.001 };
  let seconds = 0;
  let matched = false;
  let match;
  while ((match = pattern.exec(duration)) !== null) {
    seconds += parseFloat(match[1]) * unitSeconds[match[2]];
    matched = true;
  }
  if (!matched) {
    throw new Error(`Unsupported duration: ${duration}`);
  }
  return seconds;
}

/**
 * Stops before the test starts when the pre-signed JWTs would expire during it: PoP JWTs past the
 * tenant's iat window, and Client Attestation JWTs past their exp, all answer 401.
 */
export function assertTestFitsWindow({ generatedAt, popWindowSeconds, duration, resignCommand }) {
  if (!popWindowSeconds) {
    throw new Error(`Pre-signed JWTs have no popWindowSeconds. Run: ${resignCommand}`);
  }
  const ageSeconds = Math.floor(Date.now() / 1000) - generatedAt;
  const endsAt = ageSeconds + durationSeconds(duration);
  if (endsAt > popWindowSeconds - MARGIN_SECONDS) {
    throw new Error(
      `The test would end ${endsAt}s after the JWTs were signed, beyond the ${popWindowSeconds}s window ` +
        `(minus ${MARGIN_SECONDS}s margin). Run: ${resignCommand}`
    );
  }
}

/**
 * The index of the PoP JWT this request uses: unique across all VUs, so no PoP JWT is sent twice.
 * Aborts the test once the pool is used up, rather than letting replayed JWTs fail as 401.
 */
export function nextPopIndex(popCount, resignCommand) {
  const index = exec.scenario.iterationInTest;
  if (index >= popCount) {
    exec.test.abort(
      `The PoP JWT pool (${popCount}) is used up. Sign more with --requests, or shorten DURATION. ` +
        `Run: ${resignCommand}`
    );
  }
  return index;
}

/** The pre-signed pair for this request: the k-th PoP JWT and the instance that signed it. */
export function presignedPair(instances, popJwts, resignCommand) {
  const index = nextPopIndex(popJwts.length, resignCommand);
  return {
    attestationJwt: instances[index % instances.length].attestationJwt,
    popJwt: popJwts[index],
  };
}

// =============================================================================
// POP_SIGNING=k6
// =============================================================================

const ATTESTATION_TYP = 'oauth-client-attestation+jwt';
const POP_TYP = 'oauth-client-attestation-pop+jwt';

/** Lifetime of the Client Attestation JWTs signed in k6; re-signed after half of it. */
const ATTESTATION_LIFETIME_SECONDS = parseInt(__ENV.ATTESTATION_LIFETIME_SECONDS || '300');

/** WebCrypto parameters per JWS alg. */
const WEB_CRYPTO = {
  ES256: { key: { name: 'ECDSA', namedCurve: 'P-256' }, sign: { name: 'ECDSA', hash: 'SHA-256' } },
  ES384: { key: { name: 'ECDSA', namedCurve: 'P-384' }, sign: { name: 'ECDSA', hash: 'SHA-384' } },
  ES512: { key: { name: 'ECDSA', namedCurve: 'P-521' }, sign: { name: 'ECDSA', hash: 'SHA-512' } },
  RS256: {
    key: { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    sign: { name: 'RSASSA-PKCS1-v1_5' },
  },
  PS256: { key: { name: 'RSA-PSS', hash: 'SHA-256' }, sign: { name: 'RSA-PSS', saltLength: 32 } },
  // EdDSA は無い: k6 の WebCrypto は Ed25519 の署名に未対応（NotSupportedError）
};

const PRIVATE_MEMBERS = ['d', 'p', 'q', 'dp', 'dq', 'qi'];

const base64url = (input) => encoding.b64encode(input, 'rawurl');

/** k6 has no TextEncoder; a JWS signing input is base64url, so plain ASCII. */
const asciiBytes = (text) => {
  const bytes = new Uint8Array(text.length);
  for (let i = 0; i < text.length; i++) bytes[i] = text.charCodeAt(i);
  return bytes;
};

const publicJwkOf = (jwk) => {
  const publicJwk = {};
  for (const [name, value] of Object.entries(jwk)) {
    if (!PRIVATE_MEMBERS.includes(name)) publicJwk[name] = value;
  }
  return publicJwk;
};

/**
 * Signs the Client Attestation JWT and PoP JWT in k6 (POP_SIGNING=k6).
 *
 * The PoP JWT is signed for every request with a fresh jti and iat, so neither the iat window nor
 * a pool size limits the test. The Client Attestation JWT is signed once per instance and reused
 * — the draft allows that — and signed again once half of its lifetime has passed.
 *
 * @param {object} params
 * @param {string} params.alg JWS alg
 * @param {string} params.issuer aud of the PoP JWT
 * @param {string} params.clientId sub of the Client Attestation JWT
 * @param {Array<{instanceId: string, jwk: object}>} params.instances private JWKs
 * @param {object} [params.attesterJwk] private JWK of the Attester (attester_jwks); the instance
 *     signs its own Client Attestation JWT when absent (registered_instance_key)
 */
export function createK6Signer({ alg, issuer, clientId, instances, attesterJwk }) {
  const webCrypto = WEB_CRYPTO[alg];
  if (!webCrypto) {
    throw new Error(
      `POP_SIGNING=k6 does not support alg ${alg} (k6 WebCrypto signs ${Object.keys(WEB_CRYPTO).join(', ')}). ` +
        'Use POP_SIGNING=presigned.'
    );
  }
  const header = (extra) => base64url(JSON.stringify({ alg, ...extra }));
  const popHeader = header({ typ: POP_TYP });

  const importPrivateKey = (jwk) => {
    const { kid, use, alg: jwkAlg, ...material } = jwk;
    return crypto.subtle.importKey('jwk', material, webCrypto.key, false, ['sign']);
  };

  const sign = async (key, encodedHeader, payload) => {
    const signingInput = `${encodedHeader}.${base64url(JSON.stringify(payload))}`;
    // ECDSA は r||s（IEEE P1363）で返るので、JWS の署名の形式そのまま
    const signature = await crypto.subtle.sign(webCrypto.sign, key, asciiBytes(signingInput));
    return `${signingInput}.${base64url(signature)}`;
  };

  const instanceKeys = new Map();
  const attestations = new Map();
  let attesterKey = null;

  const instanceKey = async (index) => {
    if (!instanceKeys.has(index)) {
      instanceKeys.set(index, await importPrivateKey(instances[index].jwk));
    }
    return instanceKeys.get(index);
  };

  const attestationJwt = async (index, now) => {
    const cached = attestations.get(index);
    if (cached && now - cached.iat < ATTESTATION_LIFETIME_SECONDS / 2) {
      return cached.jwt;
    }
    const instance = instances[index];
    let key;
    let kid;
    if (attesterJwk) {
      if (!attesterKey) attesterKey = await importPrivateKey(attesterJwk);
      key = attesterKey;
      kid = attesterJwk.kid;
    } else {
      key = await instanceKey(index);
      kid = instance.instanceId;
    }
    const jwt = await sign(key, header({ typ: ATTESTATION_TYP, kid }), {
      sub: clientId,
      iat: now,
      exp: now + ATTESTATION_LIFETIME_SECONDS,
      cnf: { jwk: publicJwkOf(instance.jwk) },
    });
    attestations.set(index, { jwt, iat: now });
    return jwt;
  };

  return {
    /** The pair for this request, signed now; the instance rotates with the request number. */
    async next() {
      const index = exec.scenario.iterationInTest % instances.length;
      const now = Math.floor(Date.now() / 1000);
      return {
        attestationJwt: await attestationJwt(index, now),
        popJwt: await sign(await instanceKey(index), popHeader, {
          aud: issuer,
          jti: crypto.randomUUID(),
          iat: now,
        }),
      };
    },
  };
}
