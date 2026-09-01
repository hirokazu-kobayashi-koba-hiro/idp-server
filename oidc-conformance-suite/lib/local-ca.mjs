/*
 * ローカル環境の CA 証明書を探す。
 *
 * docker/nginx/certs/*.pem は mkcert が生成するもので gitignore されている。そのため
 * git worktree のような「チェックアウトしたばかりの作業ツリー」には存在せず、メインの
 * チェックアウトにしかない。毎回 IDP_ROOT_CA を指定させるのは面倒なので、worktree から
 * でも自動で見つける。
 *
 * 探索順:
 *   1. IDP_ROOT_CA（明示指定が最優先）
 *   2. このリポジトリの docker/nginx/certs/rootCA.pem
 *   3. git の共通ディレクトリから辿ったメインチェックアウトの同パス（worktree 対策）
 */
import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";

const CERT_RELATIVE_PATH = "docker/nginx/certs/rootCA.pem";

/** worktree から見た「メインのチェックアウト」を返す。worktree でなければ null。 */
function mainCheckoutDir(repoRoot) {
  try {
    // worktree では .git/worktrees/<name> ではなく共通の .git を指す
    const commonDir = execFileSync(
      "git",
      ["rev-parse", "--path-format=absolute", "--git-common-dir"],
      { cwd: repoRoot, encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] },
    ).trim();
    const candidate = path.dirname(commonDir);
    return candidate === repoRoot ? null : candidate;
  } catch {
    return null;
  }
}

export function resolveLocalCaPath() {
  if (process.env.IDP_ROOT_CA) return process.env.IDP_ROOT_CA;

  const repoRoot = path.resolve(new URL("../..", import.meta.url).pathname);
  const candidates = [path.join(repoRoot, CERT_RELATIVE_PATH)];

  const mainCheckout = mainCheckoutDir(repoRoot);
  if (mainCheckout) candidates.push(path.join(mainCheckout, CERT_RELATIVE_PATH));

  const found = candidates.find((p) => fs.existsSync(p));
  if (found) return found;

  throw new Error(
    `ローカル CA が見つかりません。探した場所:\n` +
      candidates.map((p) => `  - ${p}`).join("\n") +
      "\n" +
      "  docker/nginx/certs/*.pem は mkcert が生成するもので gitignore されています。\n" +
      "  ローカル環境を構築していない場合は README の手順を実行するか、\n" +
      "  IDP_ROOT_CA で明示的に指定してください。",
  );
}
