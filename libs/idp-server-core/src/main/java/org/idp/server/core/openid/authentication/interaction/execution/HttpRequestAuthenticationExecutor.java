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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionConfig;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionStoreConfig;
import org.idp.server.core.openid.authentication.config.AuthenticationPreviousInteractionResolveConfig;
import org.idp.server.core.openid.authentication.interaction.AuthenticationInteraction;
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionCommandRepository;
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionQueryRepository;
import org.idp.server.platform.http.HttpRequestBaseParams;
import org.idp.server.platform.http.HttpRequestExecutor;
import org.idp.server.platform.http.HttpRequestResult;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

public class HttpRequestAuthenticationExecutor implements AuthenticationExecutor {

  AuthenticationInteractionCommandRepository interactionCommandRepository;
  AuthenticationInteractionQueryRepository interactionQueryRepository;
  HttpRequestExecutor httpRequestExecutor;
  JsonConverter jsonConverter;

  public HttpRequestAuthenticationExecutor(
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
    return "http_request";
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

    HttpRequestBaseParams httpRequestBaseParams = new HttpRequestBaseParams(param);

    HttpRequestResult executionResult =
        httpRequestExecutor.execute(configuration.httpRequest(), httpRequestBaseParams);

    if (executionResult.isClientError()) {
      return AuthenticationExecutionResult.clientError(executionResult.body().toMap());
    }

    if (executionResult.isServerError()) {
      return AuthenticationExecutionResult.serverError(executionResult.body().toMap());
    }

    if (configuration.hasHttpRequestStore()) {
      AuthenticationExecutionStoreConfig httpRequestStore = configuration.httpRequestStore();
      JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromMap(executionResult.toMap());
      JsonPathWrapper pathWrapper = new JsonPathWrapper(jsonNodeWrapper.toJson());
      Map<String, Object> interactionMap =
          MappingRuleObjectMapper.execute(httpRequestStore.interactionMappingRules(), pathWrapper);
      interactionCommandRepository.register(
          tenant, identifier, httpRequestStore.key(), interactionMap);
    }

    Map<String, Object> interactionMap = new HashMap<>();
    interactionMap.put("execution_http_request", executionResult.toMap());
    return AuthenticationExecutionResult.success(interactionMap);
  }
}
