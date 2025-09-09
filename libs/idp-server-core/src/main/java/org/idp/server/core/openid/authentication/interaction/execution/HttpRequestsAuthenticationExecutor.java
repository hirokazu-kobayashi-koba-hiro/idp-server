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
import org.idp.server.platform.mapper.MappingRuleObjectMapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.oauth.OAuthAuthorizationResolvers;
import org.idp.server.platform.type.RequestAttributes;

public class HttpRequestsAuthenticationExecutor implements AuthenticationExecutor {

  AuthenticationInteractionCommandRepository interactionCommandRepository;
  AuthenticationInteractionQueryRepository interactionQueryRepository;
  HttpRequestExecutor httpRequestExecutor;
  JsonConverter jsonConverter;

  public HttpRequestsAuthenticationExecutor(
      AuthenticationInteractionCommandRepository interactionCommandRepository,
      AuthenticationInteractionQueryRepository interactionQueryRepository,
      OAuthAuthorizationResolvers oauthAuthorizationResolvers) {
    this.interactionCommandRepository = interactionCommandRepository;
    this.interactionQueryRepository = interactionQueryRepository;
    this.httpRequestExecutor =
        new HttpRequestExecutor(HttpClientFactory.defaultClient(), oauthAuthorizationResolvers);
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
    param.put("request_attributes", requestAttributes);

    if (configuration.hasPreviousInteraction()) {
      AuthenticationPreviousInteractionResolveConfig previousInteraction =
          configuration.previousInteraction();
      AuthenticationInteraction authenticationInteraction =
          interactionQueryRepository.find(tenant, identifier, previousInteraction.key());
      param.put("interaction", authenticationInteraction.payload());
    }

    List<HttpRequestExecutionConfig> httpRequestExecutionConfigs = configuration.httpRequests();

    Map<String, Object> results = new HashMap<>();
    List<HttpRequestResult> httpRequestResults = new ArrayList<>();
    for (HttpRequestExecutionConfig httpRequestExecutionConfig : httpRequestExecutionConfigs) {

      HttpRequestBaseParams httpRequestBaseParams = new HttpRequestBaseParams(param);
      HttpRequestResult executionResult =
          httpRequestExecutor.execute(httpRequestExecutionConfig, httpRequestBaseParams);

      if (executionResult.isClientError()) {
        return AuthenticationExecutionResult.clientError(executionResult.body().toMap());
      }

      if (executionResult.isServerError()) {
        return AuthenticationExecutionResult.serverError(executionResult.body().toMap());
      }

      httpRequestResults.add(executionResult);
      param.put(
          "execution_http_requests",
          httpRequestResults.stream().map(HttpRequestResult::toMap).toList());
    }

    results.put(
        "execution_http_requests",
        httpRequestResults.stream().map(HttpRequestResult::toMap).toList());

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
}
