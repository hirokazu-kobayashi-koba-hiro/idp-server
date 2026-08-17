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

package org.idp.server.core.openid.authentication.interaction.execution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionConfig;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionStoreConfig;
import org.idp.server.core.openid.authentication.config.AuthenticationPreviousInteractionResolveConfig;
import org.idp.server.core.openid.authentication.interaction.AuthenticationInteraction;
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionCommandRepository;
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionQueryRepository;
import org.idp.server.platform.http.*;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

public class HttpRequestsAuthenticationExecutor implements AuthenticationExecutor {

  AuthenticationInteractionCommandRepository interactionCommandRepository;
  AuthenticationInteractionQueryRepository interactionQueryRepository;
  HttpRequestExecutor httpRequestExecutor;
  JsonConverter jsonConverter;
  LoggerWrapper log = LoggerWrapper.getLogger(HttpRequestsAuthenticationExecutor.class);

  /**
   * Marks the entry standing in for a request that was not sent (#1789).
   *
   * <p>Part of the configuration contract, not an internal detail: mapping rules identify a skipped
   * slot with {@code {"operation": "missing", "path": "$.execution_http_requests[N].skipped"}}, so
   * the key is depended on from outside this class.
   */
  private static final String SKIPPED_KEY = "skipped";

  public HttpRequestsAuthenticationExecutor(
      AuthenticationInteractionCommandRepository interactionCommandRepository,
      AuthenticationInteractionQueryRepository interactionQueryRepository,
      HttpRequestExecutor httpRequestExecutor) {
    this.interactionCommandRepository = interactionCommandRepository;
    this.interactionQueryRepository = interactionQueryRepository;
    this.httpRequestExecutor = httpRequestExecutor;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public String function() {
    return "http_requests";
  }

  @Override
  public AuthenticationExecutionResult execute(
      Tenant tenant,
      AuthenticationTransactionIdentifier identifier,
      AuthenticationExecutionRequest request,
      RequestAttributes requestAttributes,
      AuthenticationExecutionConfig configuration) {

    Map<String, Object> param = new HashMap<>();
    param.put("request_body", request.toMap());
    // Issue #1773: see HttpRequestAuthenticationExecutor — the object was serialized as
    // {"json_node_wrapper":{"json_node":{...}}} instead of the attribute map itself.
    param.put("request_attributes", requestAttributes.toMap());

    // Issue #1767: the $.user.* projection of #1439 was wired only into the singular http_request
    // executor, so the same mapping rule worked or silently resolved to null depending on which
    // executor the interaction happened to use. Mirror it here.
    if (request.hasTransactionUser()) {
      param.put("user", request.transactionUser());
    }

    if (configuration.hasPreviousInteraction()) {
      AuthenticationPreviousInteractionResolveConfig previousInteraction =
          configuration.previousInteraction();
      AuthenticationInteraction authenticationInteraction =
          interactionQueryRepository.find(tenant, identifier, previousInteraction.key());
      param.put("interaction", authenticationInteraction.payload());
    }

    List<HttpRequestExecutionConfig> httpRequestExecutionConfigs = configuration.httpRequests();

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

      executionRecords.add(executionResult.toMap());

      // If error occurs, return immediately with all results collected so far
      if (executionResult.isClientError() || executionResult.isServerError()) {
        return createErrorResult(executionRecords, executionResult);
      }

      param.put("execution_http_requests", List.copyOf(executionRecords));
    }

    if (nothingRan(executionRecords)) {
      return noExecutionResult(executionRecords);
    }

    Map<String, Object> results = new HashMap<>();
    results.put("execution_http_requests", List.copyOf(executionRecords));

    if (configuration.hasHttpRequestsStore()) {
      AuthenticationExecutionStoreConfig httpRequestStore = configuration.httpRequestsStore();
      JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromMap(results);
      JsonPathWrapper pathWrapper = new JsonPathWrapper(jsonNodeWrapper.toJson());
      Map<String, Object> interactionMap =
          MappingRuleObjectMapper.execute(httpRequestStore.interactionMappingRules(), pathWrapper);
      interactionCommandRepository.register(
          tenant, identifier, httpRequestStore.key(), interactionMap);
    }

    return AuthenticationExecutionResult.success(results);
  }

  /**
   * Builds the execution result from the request that stopped the chain.
   *
   * <p><b>Issue #1783:</b> the status has to be carried, not just its class. Answering {@code
   * clientError()} / {@code serverError()} flattened every failure to 400 or 500, so a {@code
   * response_resolve_configs} mapping to 429 or 503 was silently discarded here — while the same
   * configuration on {@link HttpRequestAuthenticationExecutor} (the single-request executor) came
   * through intact. The configuration validates and stores identically either way, so the two only
   * diverged at runtime.
   *
   * <p>429 is the case that mattered: flattened to 400, a client cannot tell "you sent something
   * wrong" from "we are rate limiting you", which inverts the reason for mapping it in the first
   * place.
   */
  private AuthenticationExecutionResult createErrorResult(
      List<Map<String, Object>> executionRecords, HttpRequestResult failedResult) {
    Map<String, Object> response = new HashMap<>();
    response.put("execution_http_requests", List.copyOf(executionRecords));

    return AuthenticationExecutionResult.error(failedResult.statusCode(), response);
  }

  /**
   * Decides whether the configured {@code condition} lets this request run (#1789).
   *
   * <p>Evaluated against the same context the mapping rules see — {@code $.request_body} / {@code
   * $.request_attributes} / {@code $.user} / {@code $.interaction} / {@code
   * $.execution_http_requests} — so a request can be gated on what an earlier one answered. No
   * condition means run, which is what every configuration written before this existed says.
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
          "Skipping http request due to condition evaluation. url={}, condition={}",
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
   * Fails a chain in which nothing ran (#1789).
   *
   * <p>Skipping is not automatically the safe side. The execution <em>is</em> the check — an
   * external password verification, a risk assessment — so a chain where every request was skipped
   * has verified nothing, and reporting success would let the step pass without the external
   * service ever being consulted.
   *
   * <p>The most likely way to get here is a mistyped path: an unresolved JSONPath is null rather
   * than an error (#1646), so {@code ConditionSpec} logs nothing and returns false, and the
   * per-request skip is debug. Succeeding quietly would make that indistinguishable from a healthy
   * run.
   *
   * <p>Failing costs no compatibility: this state is unreachable without {@code condition}, which
   * arrives with #1789. A configuration that legitimately wants "call nothing this time" can leave
   * one request unconditional; if a real need for the permissive behaviour turns up it can be opted
   * into later, whereas the reverse would be a breaking change.
   *
   * <p>Ordinary branching, where some requests ran, is unaffected.
   */
  private AuthenticationExecutionResult noExecutionResult(
      List<Map<String, Object>> executionRecords) {
    log.warn(
        "All configured http requests were skipped by their conditions. count={}. "
            + "Nothing was executed, so the interaction fails instead of reporting success.",
        executionRecords.size());

    Map<String, Object> response = new HashMap<>();
    response.put("execution_http_requests", List.copyOf(executionRecords));
    response.put("error", "server_error");
    response.put(
        "error_description",
        "All configured http requests were skipped by their conditions. No external call was made.");

    return AuthenticationExecutionResult.error(500, response);
  }

  /**
   * Placeholder kept in place of a skipped request.
   *
   * <p>{@code execution_http_requests[N]} means "the Nth configured request", not "the Nth request
   * that ran". Dropping the entry would renumber everything after it, so a mapping rule pointing at
   * a later request would read a different one depending on whether the condition happened to hold
   * — and a JSONPath that lands on the wrong request resolves to null rather than failing (#1646),
   * so nothing would report it.
   */
  private Map<String, Object> skippedRecord() {
    Map<String, Object> record = new HashMap<>();
    record.put(SKIPPED_KEY, true);
    return record;
  }
}
