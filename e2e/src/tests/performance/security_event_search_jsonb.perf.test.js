import { describe, expect, it, beforeAll } from "@jest/globals";
import { get } from "../../lib/http";
import { backendUrl } from "../testConfig";
import { requestToken } from "../../api/oauthClient";

/**
 * security_event の detail JSONB 検索性能を E2E で計測するための perf テスト。
 *
 * 前提:
 *   1. libs/idp-server-database/postgresql/operation/security-event-jsonb-search/bench_setup.sql
 *      で 100,000 件の type = 'BENCH_SECURITY_EVENT' を投入済みであること
 *   2. pg_stat_statements_reset() を実行済みであること (本テスト実行前)
 *
 * 計測完了後:
 *   - capture_stats.sql で統計取得
 *   - bench_cleanup.sql で後片付け
 *
 * このテストは npm test では普段走らせない (performance/ 配下) ため、明示的に
 * --testPathPattern="performance/" を指定して実行すること。
 */
describe("perf: security_event JSONB search via control plane API", () => {
  const orgId = "72cf4a12-8da3-40fb-8ae4-a77e3cda95e2";
  const tenantId = "952f6906-3e95-4ed3-86b2-981f90f785f9";

  const REPEAT = parseInt(process.env.PERF_REPEAT || "10", 10);
  let accessToken;

  beforeAll(async () => {
    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: "ito.ichiro@gmail.com",
      password: "successUserCode001",
      scope: "org-management account management",
      clientId: "org-client",
      clientSecret: "org-client-001"
    });
    expect(tokenResponse.status).toBe(200);
    accessToken = tokenResponse.data.access_token;
  });

  async function hit(query) {
    const url = `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?${query}`;
    const start = Date.now();
    const response = await get({
      url,
      headers: { Authorization: `Bearer ${accessToken}` }
    });
    const elapsed = Date.now() - start;
    expect(response.status).toBe(200);
    return { elapsed, totalCount: response.data.total_count, listLength: response.data.list.length };
  }

  async function repeat(label, query) {
    const times = [];
    let lastTotal = 0;
    for (let i = 0; i < REPEAT; i++) {
      const { elapsed, totalCount } = await hit(query);
      times.push(elapsed);
      lastTotal = totalCount;
    }
    times.sort((a, b) => a - b);
    const min = times[0];
    const max = times[times.length - 1];
    const median = times[Math.floor(times.length / 2)];
    const mean = times.reduce((a, b) => a + b, 0) / times.length;
    console.log(
      `[perf] ${label.padEnd(50)} | total_count=${lastTotal.toString().padStart(7)} | ` +
        `min=${min}ms median=${median}ms mean=${mean.toFixed(1)}ms max=${max}ms`
    );
    return { min, median, mean, max, totalCount: lastTotal };
  }

  it("baseline: type=BENCH_SECURITY_EVENT only (no detail filter)", async () => {
    const r = await repeat("type only", "event_type=BENCH_SECURITY_EVENT&limit=20");
    expect(r.totalCount).toBeGreaterThan(0);
  });

  it("high hit rate: details.outcome=success (~50%)", async () => {
    const r = await repeat(
      "outcome=success (high)",
      "event_type=BENCH_SECURITY_EVENT&details.outcome=success&limit=20"
    );
    expect(r.totalCount).toBeGreaterThan(0);
  });

  it("mid hit rate: details.status=processed (~5%)", async () => {
    const r = await repeat(
      "status=processed (mid)",
      "event_type=BENCH_SECURITY_EVENT&details.status=processed&limit=20"
    );
    expect(r.totalCount).toBeGreaterThan(0);
  });

  it("mid hit rate: details.resource=invoice (~20%)", async () => {
    const r = await repeat(
      "resource=invoice (mid)",
      "event_type=BENCH_SECURITY_EVENT&details.resource=invoice&limit=20"
    );
    expect(r.totalCount).toBeGreaterThan(0);
  });

  it("composite AND: outcome=success + method=POST (~12%)", async () => {
    const r = await repeat(
      "outcome=success + method=POST",
      "event_type=BENCH_SECURITY_EVENT&details.outcome=success&details.method=POST&limit=20"
    );
    expect(r.totalCount).toBeGreaterThan(0);
  });

  it("low hit rate: details.bench_index=99999 (~1/100k)", async () => {
    const r = await repeat(
      "bench_index=99999 (low)",
      "event_type=BENCH_SECURITY_EVENT&details.bench_index=99999&limit=20"
    );
    expect(r.totalCount).toBeGreaterThanOrEqual(0);
  });

  it("nested key (mid): details.user.sub=sub-42 (~100/100k)", async () => {
    const r = await repeat(
      "user.sub=sub-42 (nested mid)",
      "event_type=BENCH_SECURITY_EVENT&details.user.sub=sub-42&limit=20"
    );
    expect(r.totalCount).toBeGreaterThan(0);
  });

  it("nested key (low): details.user.name=user-77777 (~1/100k)", async () => {
    const r = await repeat(
      "user.name=user-77777 (nested low)",
      "event_type=BENCH_SECURITY_EVENT&details.user.name=user-77777&limit=20"
    );
    expect(r.totalCount).toBe(1);
  });
});
