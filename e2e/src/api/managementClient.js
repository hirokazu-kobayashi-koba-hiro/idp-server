import { postWithJson } from "../lib/http";
import { sleep } from "../lib/util";
import { backendUrl } from "../tests/testConfig";

/**
 * Call the onboarding API and wait for replica propagation.
 *
 * After tenant creation, DynamicCorsFilter reads tenant metadata via
 * TenantMetaDataApi.get(). When PostgreSQL reader replicas are in use,
 * the newly created tenant may not be visible immediately, causing 404.
 * The sleep gives the replica time to catch up.
 *
 * @param {Object} params
 * @param {Object} params.body - Onboarding request body
 * @param {Object} params.headers - Request headers (must include Authorization)
 * @returns {Promise<Object>} Onboarding response
 */
export const onboarding = async ({ body, headers }) => {
  const response = await postWithJson({
    url: `${backendUrl}/v1/management/onboarding`,
    body,
    headers,
  });
  await sleep(1000);
  return response;
};
