/**
 * Pre-signed Client Attestation PoP JWTs, one per request.
 *
 * サーバは受け付けた PoP JWT の jti を記録し、同じ JWT の 2 回目を拒否する（#1893 / #1941）。
 * そのため、PoP JWT は流すリクエストの数だけ、jti を変えて前もって署名しておく。
 * 署名は k6 では行わない（k6/crypto は HMAC のみ。クライアント側の署名コストを測定に混ぜないためでもある）。
 *
 * PoP JWT の iat は、テナントの client_attestation_pop_acceptable_window_seconds（既定 60 秒、上限 600 秒）の
 * 内側でしか受け付けられない。性能テスト用のテナントは 600 秒にしてある（scripts/templates/onboarding-template.json）。
 * 1 回のテストは、署名からこの窓の内側に収める。
 */

const crypto = require("crypto");

const POP_TYP = "oauth-client-attestation-pop+jwt";
const DEFAULT_WINDOW_SECONDS = 600;
const SIGNING_BATCH = 500;

/**
 * Signs `count` PoP JWTs, the k-th by `instances[k % instances.length]`, so a scenario that takes
 * the k-th JWT for its k-th request can find the matching Client Attestation JWT by the same index.
 *
 * @param {object} params
 * @param {Array<{ key: CryptoKey }>} params.signers one per instance, in instance order
 * @param {string} params.alg JWS alg
 * @param {string} params.issuer aud of the PoP JWT
 * @param {number} params.now iat, in seconds
 * @param {number} params.count number of PoP JWTs to sign
 * @param {string} [params.challenge] challenge claim, when one is used
 * @param {(signed: number) => void} [params.onProgress]
 * @returns {Promise<string[]>}
 */
async function signPopPool({ jose, signers, alg, issuer, now, count, challenge, onProgress }) {
  const popJwts = new Array(count);
  for (let start = 0; start < count; start += SIGNING_BATCH) {
    const end = Math.min(start + SIGNING_BATCH, count);
    const batch = [];
    for (let k = start; k < end; k++) {
      const signer = signers[k % signers.length];
      batch.push(
        new jose.SignJWT({
          aud: issuer,
          jti: crypto.randomUUID(),
          iat: now,
          ...(challenge ? { challenge } : {}),
        })
          .setProtectedHeader({ alg, typ: POP_TYP })
          .sign(signer.key)
          .then((jwt) => {
            popJwts[k] = jwt;
          })
      );
    }
    await Promise.all(batch);
    if (onProgress) onProgress(end);
  }
  return popJwts;
}

module.exports = { signPopPool, DEFAULT_WINDOW_SECONDS };
