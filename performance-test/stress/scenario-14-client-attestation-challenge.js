import http from 'k6/http';
import { check } from 'k6';

// 環境変数でカスタマイズ可能なパラメータ
const VU_COUNT = parseInt(__ENV.VU_COUNT || '120');
const DURATION = __ENV.DURATION || '30s';

export let options = {
  vus: VU_COUNT,
  duration: DURATION,
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

// 設定ファイルから読み込み
const tenantData = JSON.parse(open('../data/performance-test-multi-tenant-users.json'));

const tenantIndex = parseInt(__ENV.TENANT_INDEX || '0');
const config = tenantData[tenantIndex];

/**
 * Challenge 発行エンドポイント（draft-ietf-oauth-attestation-based-client-auth-10 Section 6.1）
 *
 * 未認証エンドポイントで、1リクエストにつき client_attestation_challenge へ 1 INSERT が走る。
 * 認証を伴わない書き込み経路なので、TPS 上限と DB 書き込み増幅を把握しておく。
 * 期限切れ行は delete-expired-data で回収される。
 */
export default function () {
  const baseUrl = __ENV.BASE_URL || 'https://api.local.test';
  const tenantId = config.tenantId;

  const url = `${baseUrl}/${tenantId}/v1/client-attestation/challenges`;

  const res = http.post(url, '{}', {
    headers: {
      'Content-Type': 'application/json',
    },
  });

  check(res, {
    'status is 200': (r) => r.status === 200,
    'challenge issued': (r) => r.json('attestation_challenge') !== undefined,
  });
}
