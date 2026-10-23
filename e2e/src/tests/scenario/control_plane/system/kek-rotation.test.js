import { describe, expect, it } from "@jest/globals";
import fs from "fs";
import path from "path";
import { backendUrl } from "../../../testConfig";
import { get, postWithJson } from "../../../../lib/http";
import {
  login,
  linkOnce,
  MANAGE_SCOPE,
  ownerUser,
  READ_TOKEN_SCOPE,
  retrieveToken,
} from "../../../../lib/accountLinking";

/**
 * 鍵暗号化鍵（KEK）のローテーション。
 *
 * 連携の外部トークンはテナントごとのデータ鍵（DEK）で暗号化し、DEK は KEK で包んで保存する。
 * KEK を替えても DEK を包み直すだけで、トークンの暗号文は書き換えない。それを本物の DB と
 * 複数台のサーバーで確かめる。
 *
 * 段階の合間にサーバーを別の鍵設定で再起動するため、通常の npm test では全体を skip する。
 * e2e/scripts/kek-rotation.sh が KEY_ROTATION_PHASE を切り替えながら順に呼ぶ。
 */
const PHASE = process.env.KEY_ROTATION_PHASE;
const STATE = path.resolve(process.cwd(), ".tmp", "kek-rotation-state.json");
const OAUTH2_PROVIDER = "account-linking-oauth2";

const adminHeaders = () => ({
  Authorization: `Basic ${Buffer.from(
    `${process.env.IDP_SERVER_API_KEY}:${process.env.IDP_SERVER_API_SECRET}`
  ).toString("base64")}`,
});

const status = async () => {
  const response = await get({
    url: `${backendUrl}/v1/admin/operations/data-keys`,
    headers: adminHeaders(),
  });
  expect(response.status).toBe(200);
  return response.data;
};

const rewrap = async (maxNumber) => {
  const response = await postWithJson({
    url: `${backendUrl}/v1/admin/operations/data-keys/rewrap`,
    headers: adminHeaders(),
    body: { max_number: maxNumber },
  });
  expect(response.status).toBe(200);
  return response.data;
};

/** setup で連携したアカウントが、そのとき取り出したのと同じトークンを返すこと。 */
const expectStoredTokensUnchanged = async () => {
  const state = JSON.parse(fs.readFileSync(STATE, "utf-8"));
  const accessToken = await login(ownerUser, `openid profile email ${MANAGE_SCOPE} ${READ_TOKEN_SCOPE}`);
  for (const alias of state.aliases) {
    const response = await retrieveToken(alias, accessToken);
    expect(response.status).toBe(200);
    expect(response.data.access_token).toBe(state.tokens[alias]);
  }
};

const phase = (name) => (PHASE === name ? it : it.skip);

describe("KEK rotation (run through e2e/scripts/kek-rotation.sh)", () => {

  phase("setup")("setup: links accounts under the current KEK", async () => {
    const before = await status();
    expect(before.unopenable).toBe(0);

    const accessToken = await login(ownerUser, `openid profile email ${MANAGE_SCOPE} ${READ_TOKEN_SCOPE}`);
    const aliases = [
      await linkOnce(accessToken, OAUTH2_PROVIDER),
      await linkOnce(accessToken, OAUTH2_PROVIDER),
    ];
    const tokens = {};
    for (const alias of aliases) {
      const response = await retrieveToken(alias, accessToken);
      expect(response.status).toBe(200);
      tokens[alias] = response.data.access_token;
    }
    fs.mkdirSync(path.dirname(STATE), { recursive: true });
    fs.writeFileSync(STATE, JSON.stringify({ aliases, tokens }, null, 2));
  });

  phase("rotated")("new KEK active, old KEK passive: tokens still open, then data keys are re-wrapped", async () => {
    const before = await status();
    expect(before.active_key_id).toBe("rotation-new");
    expect(before.pending_rewrap).toBeGreaterThan(0);
    expect(before.unopenable).toBe(0);

    // Data keys still wrapped by the old KEK open, because it is still held.
    await expectStoredTokensUnchanged();

    // Re-wrapping is bounded per call and continues where it left off.
    const first = await rewrap(1);
    expect(first.rewrapped).toBe(1);
    expect(first.pending_rewrap).toBe(before.pending_rewrap - 1);

    let pending = first.pending_rewrap;
    while (pending > 0) {
      const next = await rewrap(100);
      expect(next.rewrapped).toBeGreaterThan(0);
      pending = next.pending_rewrap;
    }
    const after = await status();
    expect(after.pending_rewrap).toBe(0);

    // The tokens themselves were never rewritten.
    await expectStoredTokensUnchanged();
  });

  phase("old-removed")("old KEK removed: nothing depends on it any more", async () => {
    const current = await status();
    expect(current.configured_key_ids).toEqual(["rotation-new"]);
    expect(current.unopenable).toBe(0);
    await expectStoredTokensUnchanged();
  });

  phase("removed-too-early")("a KEK removed while data keys still need it is reported", async () => {
    const current = await status();
    expect(current.unopenable).toBeGreaterThan(0);
    const missing = current.key_encryption_keys.filter((it) => !it.configured);
    expect(missing.map((it) => it.key_id)).toContain("rotation-new");
  });

  phase("restored")("back to the original KEK: re-wrapped again and every token opens", async () => {
    let pending = (await status()).pending_rewrap;
    while (pending > 0) {
      pending = (await rewrap(100)).pending_rewrap;
    }
    expect((await status()).unopenable).toBe(0);
    await expectStoredTokensUnchanged();
  });
});
