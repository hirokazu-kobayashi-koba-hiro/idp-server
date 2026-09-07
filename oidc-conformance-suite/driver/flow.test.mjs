/*
 * verifyPasskeyBindings() の判定。
 *
 * この関数は false になるとドライバが起動しない位置にあるため、判定を間違えると
 * 全モジュールが走らない。ファイルの有無・userHandle の形・一致/不一致を固定する。
 *
 * 実行: npm test
 */
import { test } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

import { verifyPasskeyBindings } from "./flow.mjs";

const dir = fs.mkdtempSync(path.join(os.tmpdir(), "driver-passkey-"));

/** userHandle は利用者の email が base64 で入る。サーバの webauthn_credentials.user_id と同じ形。 */
function writePasskey(name, userHandle) {
  const file = path.join(dir, name);
  const body = userHandle === null ? {} : { userHandle: Buffer.from(userHandle).toString("base64") };
  fs.writeFileSync(file, JSON.stringify(body));
  return file;
}

const tenants = {
  t1: { label: "t1", signIn: "otp-passkey", email: "owner@example.com" },
};
const check = (file) => verifyPasskeyBindings({ tenants, resolveFile: () => file });

test("鍵ファイルが無ければ問題としない（画面が登録フローを出すので正常）", () => {
  assert.deepEqual(check(path.join(dir, "absent.json")), []);
});

test("持ち主と email が一致すれば問題なし", () => {
  assert.deepEqual(check(writePasskey("match.json", "owner@example.com")), []);
});

test("持ち主と email が食い違えば検出する", () => {
  const problems = check(writePasskey("mismatch.json", "someone-else@example.com"));
  assert.equal(problems.length, 1);
  assert.equal(problems[0].configured, "owner@example.com");
  assert.equal(problems[0].owner, "someone-else@example.com");
});

test("userHandle が無いファイルは判定材料が無いので触らない", () => {
  assert.deepEqual(check(writePasskey("no-handle.json", null)), []);
});

test("userHandle が email 形式でなければ対象外", () => {
  assert.deepEqual(check(writePasskey("not-email.json", "0123456789abcdef")), []);
});

test("password 認証のテナントは対象外", () => {
  const passwordOnly = { t1: { label: "t1", signIn: "password", email: "owner@example.com" } };
  const file = writePasskey("ignored.json", "someone-else@example.com");
  assert.deepEqual(
    verifyPasskeyBindings({ tenants: passwordOnly, resolveFile: () => file }),
    [],
  );
});

test("DRIVER_PASSKEY_FILE 指定時は突き合わせを見送る（テナント別分割が無効なため）", () => {
  const file = writePasskey("env.json", "someone-else@example.com");
  process.env.DRIVER_PASSKEY_FILE = file;
  try {
    assert.deepEqual(check(file), []);
  } finally {
    delete process.env.DRIVER_PASSKEY_FILE;
  }
});
