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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionConfig;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionStoreConfig;
import org.idp.server.core.openid.identity.contact.ContactVerificationChallenge;
import org.idp.server.platform.http.HttpRequestBaseParams;
import org.idp.server.platform.http.HttpRequestExecutor;
import org.idp.server.platform.http.HttpRequestResult;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Delegates one contact step to an external service over a single HTTP call (Issue #1416).
 *
 * <p>Line-for-line the same as {@code HttpRequestAuthenticationExecutor}: the same mapping source
 * ({@code $.request_body} / {@code $.request_attributes} / {@code $.user} / {@code $.interaction}),
 * the same {@code http_request_store} input ({@code $.response_body.*}), the same {@code
 * $.execution_http_request} result envelope. A tenant that wrote a delegated {@code
 * sms-authentication-challenge} for login can point this at it without editing anything.
 *
 * <p>Two things differ, both because there is no authentication transaction to key on:
 *
 * <ul>
 *   <li>what {@code http_request_store} maps out is returned rather than written to {@code
 *       authentication_interaction} — the caller puts it on the challenge row
 *   <li>{@code previous_interaction} resolves to that row's payload; its configured {@code key} is
 *       accepted and ignored, because a challenge holds exactly one exchange rather than a map of
 *       them
 * </ul>
 */
public class HttpRequestContactVerificationExecutor implements ContactVerificationExecutor {

  HttpRequestExecutor httpRequestExecutor;

  public HttpRequestContactVerificationExecutor(HttpRequestExecutor httpRequestExecutor) {
    this.httpRequestExecutor = httpRequestExecutor;
  }

  @Override
  public String function() {
    return "http_request";
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

    HttpRequestResult executionResult =
        httpRequestExecutor.execute(configuration.httpRequest(), new HttpRequestBaseParams(param));

    Map<String, Object> contents = new HashMap<>();
    contents.put("execution_http_request", executionResult.toMap());

    if (!executionResult.isSuccess()) {
      return ContactExecutionResult.error(executionResult.statusCode(), contents);
    }

    return ContactExecutionResult.successWithPayload(
        contents, storedPayload(configuration, executionResult));
  }

  private Map<String, Object> storedPayload(
      AuthenticationExecutionConfig configuration, HttpRequestResult executionResult) {

    if (!configuration.hasHttpRequestStore()) {
      return new HashMap<>();
    }

    AuthenticationExecutionStoreConfig store = configuration.httpRequestStore();
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromMap(executionResult.toMap());
    JsonPathWrapper pathWrapper = new JsonPathWrapper(jsonNodeWrapper.toJson());
    return MappingRuleObjectMapper.execute(store.interactionMappingRules(), pathWrapper);
  }
}
