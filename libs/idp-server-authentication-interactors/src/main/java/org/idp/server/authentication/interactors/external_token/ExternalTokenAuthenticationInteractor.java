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

package org.idp.server.authentication.interactors.external_token;

import java.util.*;
import org.idp.server.core.openid.authentication.*;
import org.idp.server.core.openid.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionConfig;
import org.idp.server.core.openid.authentication.config.AuthenticationInteractionConfig;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutionRequest;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutionResult;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutor;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutors;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserStatus;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.mapper.MappingRule;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

public class ExternalTokenAuthenticationInteractor implements AuthenticationInteractor {
  AuthenticationConfigurationQueryRepository configurationRepository;
  AuthenticationExecutors authenticationExecutors;
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
  LoggerWrapper log = LoggerWrapper.getLogger(ExternalTokenAuthenticationInteractor.class);

  public ExternalTokenAuthenticationInteractor(
      AuthenticationConfigurationQueryRepository configurationRepository,
      AuthenticationExecutors authenticationExecutors) {
    this.configurationRepository = configurationRepository;
    this.authenticationExecutors = authenticationExecutors;
  }

  @Override
  public AuthenticationInteractionType type() {
    return new AuthenticationInteractionType("external-token");
  }

  @Override
  public String method() {
    return "external-token";
  }

  @Override
  public AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthenticationTransaction transaction,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes,
      UserQueryRepository userQueryRepository) {

    log.debug("ExternalTokenAuthenticationInteractor called");

    AuthenticationConfiguration configuration =
        configurationRepository.get(tenant, "external-token");

    AuthenticationInteractionConfig authenticationInteractionConfig =
        configuration.getAuthenticationConfig("external-token");
    AuthenticationExecutionConfig executionConfig = authenticationInteractionConfig.execution();
    AuthenticationExecutor executor = authenticationExecutors.get(executionConfig.function());
    AuthenticationExecutionRequest authenticationExecutionRequest =
        new AuthenticationExecutionRequest(request.toMap());
    AuthenticationExecutionResult executionResult =
        executor.execute(
            tenant,
            transaction.identifier(),
            authenticationExecutionRequest,
            requestAttributes,
            executionConfig);

    if (!executionResult.isSuccess()) {
      return AuthenticationInteractionRequestResult.error(
          executionResult.statusCode(),
          executionResult.contents(),
          type,
          operationType(),
          method(),
          DefaultSecurityEventType.external_token_authentication_failure);
    }

    Map<String, Object> mappingSource = new HashMap<>();
    mappingSource.put("request_body", request.toMap());
    // Keep top-level access for existing mapping rules ($.execution_http_requests[0]...)
    mappingSource.putAll(executionResult.contents());

    User user =
        toUser(authenticationInteractionConfig.userResolve().userMappingRules(), mappingSource);

    User exsitingUser =
        userQueryRepository.findByProvider(tenant, user.providerId(), user.externalUserId());

    if (exsitingUser.exists()) {
      user.setSub(exsitingUser.sub());
      user.setStatus(exsitingUser.status());
    } else {
      user.setSub(UUID.randomUUID().toString());
      if (!user.hasStatus()) {
        user.setStatus(UserStatus.INITIALIZED);
      }
    }

    // Apply identity policy on every authentication to ensure preferred_username is set
    // for security events (Issue #1131)
    user.applyIdentityPolicy(tenant.identityPolicyConfig());

    Map<String, Object> result = new HashMap<>();
    result.put("user", user.toMinimalizedMap());

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        operationType(),
        method(),
        user,
        result,
        DefaultSecurityEventType.external_token_authentication_success);
  }

  private User toUser(List<MappingRule> mappingRules, Map<String, Object> results) {
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromMap(results);
    JsonPathWrapper jsonPath = new JsonPathWrapper(jsonNodeWrapper.toJson());
    Map<String, Object> executed = MappingRuleObjectMapper.execute(mappingRules, jsonPath);

    return jsonConverter.read(executed, User.class);
  }
}
