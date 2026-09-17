/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.openid.identity.contact.execution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionConfig;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionStoreConfig;
import org.idp.server.core.openid.identity.contact.ContactVerificationChallenge;
import org.idp.server.platform.http.HttpRequestBaseParams;
import org.idp.server.platform.http.HttpRequestExecutionConfig;
import org.idp.server.platform.http.HttpRequestExecutor;
import org.idp.server.platform.http.HttpRequestResult;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Delegates one contact step to a configured chain of HTTP calls (Issue #1416).
 *
 * <p>Mirrors {@code HttpRequestsAuthenticationExecutor}, including the parts that are easy to get
 * subtly different:
 *
 * <ul>
 *   <li>each result is appended to {@code $.execution_http_requests} before the next request runs,
 *       so a later call can read what an earlier one answered
 *   <li>a request whose {@code condition} does not hold is skipped and leaves a {@code skipped}
 *       entry in its slot, keeping index-based mapping rules aligned (Issue #1789)
 *   <li>a chain in which every request was skipped fails: the execution <em>is</em> the check, so
 *       reporting success would let the step pass without the external service being consulted
 *   <li>an empty {@code http_requests} is not that case — it is a configuration with nothing to
 *       run, and stays success, as it was before conditions existed
 *   <li>the status of the request that stopped the chain is carried, not flattened to 400/500
 *       (Issue #1783), so a mapped 429 still reads as rate limiting
 * </ul>
 */
public class HttpRequestsContactVerificationExecutor implements ContactVerificationExecutor {

  /** Part of the configuration contract: mapping rules test for it. See {@code #1789}. */
  private static final String SKIPPED_KEY = "skipped";

  HttpRequestExecutor httpRequestExecutor;
  LoggerWrapper log = LoggerWrapper.getLogger(HttpRequestsContactVerificationExecutor.class);

  public HttpRequestsContactVerificationExecutor(HttpRequestExecutor httpRequestExecutor) {
    this.httpRequestExecutor = httpRequestExecutor;
  }

  @Override
  public String function() {
    return "http_requests";
  }

  @Override
  public ContactExecutionResult execute(
      Tenant tenant,
      ContactVerificationChallenge challenge,
      ContactExecutionRequest request,
      RequestAttributes requestAttributes,
      AuthenticationExecutionConfig configuration) {

    Map<String, Object> param =
        ContactExecutionContext.create(challenge, request, requestAttributes, configuration);

    List<Map<String, Object>> executionRecords = new ArrayList<>();
    for (HttpRequestExecutionConfig requestConfig : configuration.httpRequests()) {

      if (shouldSkip(requestConfig, param)) {
        executionRecords.add(skippedRecord());
        param.put("execution_http_requests", List.copyOf(executionRecords));
        continue;
      }

      HttpRequestResult executionResult =
          httpRequestExecutor.execute(requestConfig, new HttpRequestBaseParams(param));
      executionRecords.add(executionResult.toMap());

      if (executionResult.isClientError() || executionResult.isServerError()) {
        return ContactExecutionResult.error(
            executionResult.statusCode(), contents(executionRecords));
      }

      param.put("execution_http_requests", List.copyOf(executionRecords));
    }

    if (nothingRan(executionRecords)) {
      log.warn("Contact verification chain skipped every request; nothing was verified.");
      return ContactExecutionResult.clientError(contents(executionRecords));
    }

    return ContactExecutionResult.successWithPayload(
        contents(executionRecords), storedPayload(configuration, executionRecords));
  }

  private Map<String, Object> contents(List<Map<String, Object>> executionRecords) {
    Map<String, Object> contents = new HashMap<>();
    contents.put("execution_http_requests", List.copyOf(executionRecords));
    return contents;
  }

  private Map<String, Object> storedPayload(
      AuthenticationExecutionConfig configuration, List<Map<String, Object>> executionRecords) {

    if (!configuration.hasHttpRequestsStore()) {
      return new HashMap<>();
    }

    AuthenticationExecutionStoreConfig store = configuration.httpRequestsStore();
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromMap(contents(executionRecords));
    JsonPathWrapper pathWrapper = new JsonPathWrapper(jsonNodeWrapper.toJson());
    return MappingRuleObjectMapper.execute(store.interactionMappingRules(), pathWrapper);
  }

  private boolean shouldSkip(HttpRequestExecutionConfig config, Map<String, Object> param) {
    if (!config.hasCondition()) {
      return false;
    }

    JsonNodeWrapper contextNode = JsonNodeWrapper.fromMap(param);
    JsonPathWrapper contextPath = new JsonPathWrapper(contextNode.toJson());
    boolean satisfied = config.condition().evaluate(contextPath);

    if (!satisfied) {
      log.debug(
          "Skipping contact http request due to condition evaluation. url={}, condition={}",
          config.httpRequestUrl().value(),
          config.condition().toMap());
    }
    return !satisfied;
  }

  private boolean nothingRan(List<Map<String, Object>> executionRecords) {
    return !executionRecords.isEmpty()
        && executionRecords.stream().allMatch(record -> record.containsKey(SKIPPED_KEY));
  }

  private Map<String, Object> skippedRecord() {
    Map<String, Object> record = new HashMap<>();
    record.put(SKIPPED_KEY, true);
    return record;
  }
}
