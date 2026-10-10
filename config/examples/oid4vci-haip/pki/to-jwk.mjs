// <name>.key / <name>.pem を、x5c（末端の証明書のみ）付きの秘密鍵 JWK にして標準出力へ出す。
// usage: node to-jwk.mjs <name> <kid>
import fs from "node:fs";
import crypto from "node:crypto";

const [name, kid] = process.argv.slice(2);
const key = crypto.createPrivateKey(fs.readFileSync(`${name}.key`));
const pem = fs.readFileSync(`${name}.pem`, "utf8");
const der = pem.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");

const jwk = { ...key.export({ format: "jwk" }), kid, use: "sig", alg: "ES256", x5c: [der] };
process.stdout.write(JSON.stringify(jwk, null, 2) + "\n");
