/**
 * Helpers for the attest_jwt_client_auth scenarios (scenario-15 / 16 / 17).
 *
 * サーバは受け付けた PoP JWT の jti を記録し、同じ JWT の 2 回目を拒否する（#1893）。
 * PoP JWT はスクリプトで前もってリクエストの数だけ署名してあり、シナリオは 1 リクエストに 1 つずつ使う。
 */

import exec from 'k6/execution';

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
