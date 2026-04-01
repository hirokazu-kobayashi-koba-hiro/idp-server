import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';

/**
 * User Search API Stress Test
 *
 * Tests the user search/list API (GET /v1/management/tenants/{tenantId}/users)
 * with various offset values to measure pagination performance at scale.
 *
 * This test uses the admin tenant for authentication (management scope)
 * and queries the performance test tenant's users.
 *
 * Prerequisites:
 * - Performance test tenants registered via register-tenants.sh
 * - Large user dataset imported (e.g., 200K+ users per tenant)
 *
 * Environment variables:
 * - ADMIN_TENANT_ID: Admin tenant ID (from .env)
 * - ADMIN_CLIENT_ID: Admin client ID alias (from .env)
 * - ADMIN_CLIENT_SECRET: Admin client secret (from .env)
 * - ADMIN_USER_EMAIL: Admin user email (from .env)
 * - ADMIN_USER_PASSWORD: Admin user password (from .env)
 */
const VU_COUNT = parseInt(__ENV.VU_COUNT || '20');
const DURATION = __ENV.DURATION || '30s';

export let options = {
  vus: VU_COUNT,
  duration: DURATION,
  thresholds: {
    http_req_duration: ['p(95)<2000'],
    http_req_failed: ['rate<0.01'],
  },
};

const tenantData = JSON.parse(open('../data/performance-test-tenant.json'));
const tenantIndex = parseInt(__ENV.TENANT_INDEX || '0');
const targetTenantId = tenantData[tenantIndex].tenantId;

// Admin credentials from environment variables
const adminTenantId = __ENV.ADMIN_TENANT_ID || '';
const adminClientId = __ENV.ADMIN_CLIENT_ID || '';
const adminClientSecret = __ENV.ADMIN_CLIENT_SECRET || '';
const adminUserEmail = __ENV.ADMIN_USER_EMAIL || '';
const adminUserPassword = __ENV.ADMIN_USER_PASSWORD || '';

export function setup() {
  const baseUrl = __ENV.BASE_URL || 'https://api.local.test';
  const tokenUrl = `${baseUrl}/${adminTenantId}/v1/tokens`;

  const payload =
    `grant_type=password` +
    `&client_id=${encodeURIComponent(adminClientId)}` +
    `&client_secret=${encodeURIComponent(adminClientSecret)}` +
    `&username=${encodeURIComponent(adminUserEmail)}` +
    `&password=${encodeURIComponent(adminUserPassword)}` +
    `&scope=management`;

  const tokenRes = http.post(tokenUrl, payload, {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  });

  check(tokenRes, {
    'token request succeeded': (r) => r.status === 200,
  });

  if (tokenRes.status !== 200) {
    console.error(`Token request failed: ${tokenRes.status} ${tokenRes.body}`);
    return { accessToken: null };
  }

  const accessToken = JSON.parse(tokenRes.body).access_token;
  console.log(`Token obtained. Target tenant: ${targetTenantId}`);

  return { accessToken };
}

const limit = parseInt(__ENV.SEARCH_LIMIT || '20');
const offsets = [0, 100, 1000, 10000, 50000, 100000];

export default function (data) {
  if (!data.accessToken) {
    console.error('No access token available, skipping iteration');
    return;
  }

  const baseUrl = __ENV.BASE_URL || 'https://api.local.test';
  const offset = offsets[Math.floor(Math.random() * offsets.length)];
  const url = `${baseUrl}/v1/management/tenants/${targetTenantId}/users?limit=${limit}&offset=${offset}`;

  const res = http.get(url, {
    headers: {
      Authorization: `Bearer ${data.accessToken}`,
    },
    tags: { offset: String(offset) },
  });

  check(res, {
    'status is 200': (r) => r.status === 200,
    'has list': (r) => {
      try {
        return JSON.parse(r.body).list !== undefined;
      } catch {
        return false;
      }
    },
    'has total_count': (r) => {
      try {
        return JSON.parse(r.body).total_count !== undefined;
      } catch {
        return false;
      }
    },
  });
}
