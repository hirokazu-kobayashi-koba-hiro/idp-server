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

package org.idp.server.federation.sso.oidc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.http.*;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.log.LoggerWrapper;

public class UserinfoHttpRequestsExecutor implements UserinfoExecutor {

  HttpRequestExecutor httpRequestExecutor;
  JsonConverter jsonConverter;
  LoggerWrapper log = LoggerWrapper.getLogger(UserinfoHttpRequestsExecutor.class);

  /**
   * Marks the entry standing in for a request that was not sent (#1789).
   *
   * <p>Part of the configuration contract, not an internal detail: mapping rules identify a skipped
   * slot with {@code {"operation": "missing", "path": "$.execution_http_requests[N].skipped"}}, so
   * the key is depended on from outside this class.
   */
  private static final String SKIPPED_KEY = "skipped";

  public UserinfoHttpRequestsExecutor(HttpRequestExecutor httpRequestExecutor) {
    this.httpRequestExecutor = httpRequestExecutor;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public String function() {
    return "http_requests";
  }

  @Override
  public UserinfoExecutionResult execute(
      UserinfoExecutionRequest request, OAuthExtensionUserinfoExecutionConfig configuration) {

    Map<String, Object> param = new HashMap<>();
    param.put("request_body", request.toMap());

    List<HttpRequestExecutionConfig> httpRequestExecutionConfigs = configuration.httpRequests();

    Map<String, Object> results = new HashMap<>();
    // One entry per *configured* request, skipped ones included (#1789). See skippedRecord().
    List<Map<String, Object>> executionRecords = new ArrayList<>();
    for (HttpRequestExecutionConfig httpRequestExecutionConfig : httpRequestExecutionConfigs) {

      if (shouldSkip(httpRequestExecutionConfig, param)) {
        executionRecords.add(skippedRecord());
        param.put("execution_http_requests", List.copyOf(executionRecords));
        continue;
      }

      HttpRequestBaseParams httpRequestBaseParams = new HttpRequestBaseParams(param);
      HttpRequestResult executionResult =
          httpRequestExecutor.execute(httpRequestExecutionConfig, httpRequestBaseParams);

      // #1800: carry the resolved status rather than flattening it to 400 / 500.
      if (executionResult.isClientError() || executionResult.isServerError()) {
        return UserinfoExecutionResult.error(
            executionResult.statusCode(), executionResult.body().toMap());
      }

      executionRecords.add(executionResult.toMap());
      param.put("execution_http_requests", List.copyOf(executionRecords));
    }

    if (nothingRan(executionRecords)) {
      return noExecutionResult(executionRecords);
    }

    results.put("userinfo_execution_http_requests", List.copyOf(executionRecords));

    return UserinfoExecutionResult.success(results);
  }

  /**
   * Decides whether the configured {@code condition} lets this request run (#1789).
   *
   * <p>Evaluated against the same context the mapping rules see — {@code $.request_body} and {@code
   * $.execution_http_requests} — so a request can be gated on what an earlier one answered. No
   * condition means run, which is what every configuration written before this existed says.
   *
   * <p>Kept identical to {@code HttpRequestsAuthenticationExecutor}: {@code condition} lives on the
   * shared {@code HttpRequestExecutionConfig}, so honouring it in only one of the two list
   * executors would make the same configuration work or not depending on where it was written.
   */
  private boolean shouldSkip(HttpRequestExecutionConfig config, Map<String, Object> param) {
    if (!config.hasCondition()) {
      return false;
    }

    JsonNodeWrapper contextNode = JsonNodeWrapper.fromMap(param);
    JsonPathWrapper contextPath = new JsonPathWrapper(contextNode.toJson());
    boolean satisfied = config.condition().evaluate(contextPath);

    if (!satisfied) {
      log.debug(
          "Skipping userinfo http request due to condition evaluation. url={}, condition={}",
          config.httpRequestUrl().value(),
          config.condition().toMap());
    }
    return !satisfied;
  }

  /**
   * Whether every configured request was skipped (#1789).
   *
   * <p>An empty {@code http_requests} is not this case — that is a configuration with nothing to
   * run, which behaved as success before conditions existed and still does.
   */
  private boolean nothingRan(List<Map<String, Object>> executionRecords) {
    return !executionRecords.isEmpty()
        && executionRecords.stream().allMatch(record -> record.containsKey(SKIPPED_KEY));
  }

  /**
   * Fails a userinfo chain in which nothing ran (#1789).
   *
   * <p>The chain is how the upstream IdP's userinfo is obtained. If every request was skipped there
   * is nothing to map a user from, so reporting success would resolve a user out of an empty
   * response rather than out of the IdP.
   *
   * <p>The most likely way to get here is a mistyped path: an unresolved JSONPath is null rather
   * than an error (#1646), so {@code ConditionSpec} logs nothing and returns false, and the
   * per-request skip is debug.
   *
   * <p>Kept identical to {@code HttpRequestsAuthenticationExecutor}. Failing costs no
   * compatibility: this state is unreachable without {@code condition}, which arrives with #1789.
   *
   * <p>500 is the right code on its own terms: nothing was requested, so there is no upstream
   * status to report and the fault is in this server's configuration.
   */
  private UserinfoExecutionResult noExecutionResult(List<Map<String, Object>> executionRecords) {
    log.warn(
        "All configured userinfo http requests were skipped by their conditions. count={}. "
            + "Nothing was executed, so the userinfo request fails instead of reporting success.",
        executionRecords.size());

    Map<String, Object> response = new HashMap<>();
    response.put("userinfo_execution_http_requests", List.copyOf(executionRecords));
    response.put("error", "server_error");
    response.put(
        "error_description",
        "All configured http requests were skipped by their conditions. No external call was made.");

    return UserinfoExecutionResult.error(500, response);
  }

  /**
   * Placeholder kept in place of a skipped request.
   *
   * <p>{@code userinfo_execution_http_requests[N]} means "the Nth configured request", not "the Nth
   * request that ran". Dropping the entry would renumber everything after it, so a mapping rule
   * pointing at a later request would read a different one depending on whether the condition
   * happened to hold — and a JSONPath that lands on the wrong request resolves to null rather than
   * failing (#1646), so nothing would report it.
   */
  private Map<String, Object> skippedRecord() {
    Map<String, Object> record = new HashMap<>();
    record.put(SKIPPED_KEY, true);
    return record;
  }
}
