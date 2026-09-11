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

package org.idp.server.contact.adapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionConfig;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionStoreConfig;
import org.idp.server.core.openid.identity.contact.ContactChallengeStart;
import org.idp.server.core.openid.identity.contact.ContactVerificationChallenge;
import org.idp.server.core.openid.identity.contact.ContactVerificationOperation;
import org.idp.server.platform.http.HttpRequestBaseParams;
import org.idp.server.platform.http.HttpRequestExecutionConfig;
import org.idp.server.platform.http.HttpRequestExecutor;
import org.idp.server.platform.http.HttpRequestResult;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;

/**
 * Runs the contact code exchange against an external verification service (Issue #1416).
 *
 * <p>Used when the tenant's {@code {channel}-authentication-challenge} declares {@code
 * execution.function: "http_request"}, meaning the external service generates the code, delivers
 * it, and later decides whether a submitted code is right. idp-server never sees the code.
 *
 * <h2>What is borrowed from the login path, and what is not</h2>
 *
 * The request shape, the {@code http_request_store} mapping and the {@code previous_interaction}
 * lookup are the same ones {@code HttpRequestAuthenticationExecutor} uses, so a tenant writes the
 * configuration it already knows.
 *
 * <p>That executor itself cannot be reused: it stores the exchange in {@code
 * authentication_interaction}, keyed by {@code AuthenticationTransactionIdentifier}. This feature
 * deliberately has no authentication transaction — that absence is what makes driving it from the
 * unauthenticated interaction endpoint impossible — so the reference is kept on the challenge row
 * instead, and the configured {@code previous_interaction} key selects it from there.
 */
public class ExternalContactVerificationExchange {

  static final String FUNCTION_SINGLE = "http_request";
  static final String FUNCTION_CHAIN = "http_requests";

  HttpRequestExecutor httpRequestExecutor;
  LoggerWrapper log = LoggerWrapper.getLogger(ExternalContactVerificationExchange.class);

  public ExternalContactVerificationExchange(HttpRequestExecutor httpRequestExecutor) {
    this.httpRequestExecutor = httpRequestExecutor;
  }

  /**
   * Whether the exchange is delegated rather than run by a locally configured sender.
   *
   * <p>Decided by the absence of a local sender, not by the presence of a known delegating
   * function. {@code http_request} and {@code http_requests} are the delegating shapes today, but
   * anything whose {@code execution.details} does not name a sender cannot be driven locally either
   * — and answering "not delegated" for those would send the caller down the local path to fail on
   * a null sender lookup, which is exactly the 500 this feature was reported for.
   */
  public static boolean isDelegated(AuthenticationExecutionConfig execution) {
    if (FUNCTION_SINGLE.equals(execution.function())
        || FUNCTION_CHAIN.equals(execution.function())) {
      return true;
    }
    return !hasLocalSender(execution);
  }

  /**
   * A local sender is described by {@code execution.details}; the key naming it differs per channel
   * ({@code function} for email, {@code sender_type} for SMS), so either counts.
   */
  private static boolean hasLocalSender(AuthenticationExecutionConfig execution) {
    Map<String, Object> details = execution.details();
    return isNonBlank(details.get("function")) || isNonBlank(details.get("sender_type"));
  }

  private static boolean isNonBlank(Object value) {
    return value instanceof String string && !string.isBlank();
  }

  /**
   * Asks the external service to issue and deliver a code.
   *
   * @return the reference the service will expect back at verification time
   */
  public ContactChallengeStart start(
      AuthenticationExecutionConfig execution,
      ContactVerificationOperation operation,
      String targetValue) {

    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put(operation.channel().value(), targetValue);
    requestBody.put("operation", operation.value());

    ExchangeOutcome outcome = execute(execution, requestBody, new HashMap<>());
    if (!outcome.succeeded()) {
      log.warn(
          "External contact verification challenge failed. operation={}, status={}",
          operation.value(),
          outcome.statusCode());
      return ContactChallengeStart.failure();
    }

    return ContactChallengeStart.external(storedReference(execution, outcome));
  }

  /** Asks the external service whether the submitted code matches the exchange it is holding. */
  public boolean verifyCode(
      AuthenticationExecutionConfig execution,
      ContactVerificationOperation operation,
      ContactVerificationChallenge challenge,
      String submittedCode) {

    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("verification_code", submittedCode);
    requestBody.put("operation", operation.value());

    ExchangeOutcome outcome = execute(execution, requestBody, challenge.externalReference());
    if (!outcome.succeeded()) {
      log.debug(
          "External contact verification rejected the code. operation={}, status={}",
          operation.value(),
          outcome.statusCode());
      return false;
    }
    return true;
  }

  /**
   * The stored exchange is exposed as {@code $.interaction.*}, the path a tenant already writes in
   * {@code previous_interaction} based mapping rules on the login path.
   */
  private ExchangeOutcome execute(
      AuthenticationExecutionConfig execution,
      Map<String, Object> requestBody,
      Map<String, Object> interaction) {

    Map<String, Object> param = new HashMap<>();
    param.put("request_body", requestBody);
    if (interaction != null && !interaction.isEmpty()) {
      param.put("interaction", interaction);
    }

    if (FUNCTION_CHAIN.equals(execution.function())) {
      return executeChain(execution, param);
    }

    HttpRequestResult result =
        httpRequestExecutor.execute(execution.httpRequest(), new HttpRequestBaseParams(param));
    Map<String, Object> context = new HashMap<>();
    context.put("response_body", result.toMap().get("response_body"));
    context.putAll(result.toMap());
    return new ExchangeOutcome(result.isSuccess(), result.statusCode(), context);
  }

  /**
   * Runs a configured chain, stopping at the first failure.
   *
   * <p>Mirrors {@code HttpRequestsAuthenticationExecutor}: each result is appended to {@code
   * $.execution_http_requests} so a later request — and the store mapping — can read what earlier
   * ones returned. Conditional skipping is not honoured here; a chain whose steps are conditional
   * on authentication-transaction state has no meaning in this flow.
   */
  private ExchangeOutcome executeChain(
      AuthenticationExecutionConfig execution, Map<String, Object> param) {

    List<Map<String, Object>> records = new ArrayList<>();
    for (HttpRequestExecutionConfig requestConfig : execution.httpRequests()) {
      HttpRequestResult result =
          httpRequestExecutor.execute(requestConfig, new HttpRequestBaseParams(param));
      records.add(result.toMap());
      param.put("execution_http_requests", List.copyOf(records));

      if (!result.isSuccess()) {
        Map<String, Object> failed = new HashMap<>();
        failed.put("execution_http_requests", List.copyOf(records));
        return new ExchangeOutcome(false, result.statusCode(), failed);
      }
    }

    Map<String, Object> context = new HashMap<>();
    context.put("execution_http_requests", List.copyOf(records));
    // A chain with nothing configured has verified nothing; treating that as success would let the
    // step pass without the external service ever being asked.
    return new ExchangeOutcome(!records.isEmpty(), 200, context);
  }

  /** Normalised result of either shape, so the callers do not branch twice. */
  private record ExchangeOutcome(boolean succeeded, int statusCode, Map<String, Object> context) {}

  /**
   * Applies whichever store mapping the configuration declares — {@code http_request_store} for a
   * single call, {@code http_requests_store} for a chain — to what the exchange returned.
   */
  private Map<String, Object> storedReference(
      AuthenticationExecutionConfig execution, ExchangeOutcome outcome) {

    AuthenticationExecutionStoreConfig store = null;
    if (FUNCTION_CHAIN.equals(execution.function()) && execution.hasHttpRequestsStore()) {
      store = execution.httpRequestsStore();
    } else if (execution.hasHttpRequestStore()) {
      store = execution.httpRequestStore();
    }
    if (store == null) {
      return new HashMap<>();
    }

    JsonPathWrapper pathWrapper =
        new JsonPathWrapper(JsonNodeWrapper.fromMap(outcome.context()).toJson());
    return MappingRuleObjectMapper.execute(store.interactionMappingRules(), pathWrapper);
  }
}
