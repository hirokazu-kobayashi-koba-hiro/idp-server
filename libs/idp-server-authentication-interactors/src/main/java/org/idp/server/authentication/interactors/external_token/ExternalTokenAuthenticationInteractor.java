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
import org.idp.server.core.oidc.authentication.*;
import org.idp.server.core.oidc.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.mapper.UserInfoMapper;
import org.idp.server.core.oidc.identity.repository.UserQueryRepository;
import org.idp.server.platform.http.*;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.oauth.OAuthAuthorizationResolvers;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

public class ExternalTokenAuthenticationInteractor implements AuthenticationInteractor {
  AuthenticationConfigurationQueryRepository configurationRepository;
  OAuthAuthorizationResolvers authorizationResolvers;
  HttpRequestExecutor httpRequestExecutor;
  LoggerWrapper log = LoggerWrapper.getLogger(ExternalTokenAuthenticationInteractor.class);

  public ExternalTokenAuthenticationInteractor(
      AuthenticationConfigurationQueryRepository configurationRepository) {
    this.configurationRepository = configurationRepository;
    this.authorizationResolvers = new OAuthAuthorizationResolvers();
    this.httpRequestExecutor = new HttpRequestExecutor(HttpClientFactory.defaultClient());
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

    ExternalTokenAuthenticationConfiguration configuration =
        configurationRepository.get(
            tenant, "external-token", ExternalTokenAuthenticationConfiguration.class);

    ExternalTokenAuthenticationDetailConfiguration userinfoConfig =
        configuration.userinfoDetailConfig();

    HttpRequestResult userinfoResult = execute(userinfoConfig, request, requestAttributes);

    if (userinfoResult.isClientError()) {
      return AuthenticationInteractionRequestResult.clientError(
          userinfoResult.toMap(),
          type,
          operationType(),
          method(),
          DefaultSecurityEventType.external_token_authentication_failure);
    }

    if (userinfoResult.isServerError()) {
      return AuthenticationInteractionRequestResult.serverError(
          userinfoResult.toMap(),
          type,
          operationType(),
          method(),
          DefaultSecurityEventType.external_token_authentication_failure);
    }

    UserInfoMapper userInfoMapper =
        new UserInfoMapper(
            userinfoConfig.userinfoMappingRules(),
            userinfoResult.headersAsSingleValueMap(),
            userinfoResult.body(),
            configuration.providerName());
    User user = userInfoMapper.toUser();

    User exsitingUser =
        userQueryRepository.findByProvider(
            tenant, configuration.providerName(), user.externalUserId());
    if (exsitingUser.exists()) {
      user.setSub(exsitingUser.sub());
    } else {
      user.setSub(UUID.randomUUID().toString());
    }

    Map<String, Object> result = new HashMap<>();
    result.put("user", user.toMap());

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        operationType(),
        method(),
        user,
        result,
        DefaultSecurityEventType.external_token_authentication_success);
  }

  private HttpRequestResult execute(
      ExternalTokenAuthenticationDetailConfiguration configuration,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes) {

    Map<String, Object> param = new HashMap<>();
    param.put("request_body", request.toMap());
    param.put("request_attributes", requestAttributes.toMap());
    HttpRequestBaseParams httpRequestBaseParams = new HttpRequestBaseParams(param);

    return httpRequestExecutor.execute(configuration, httpRequestBaseParams);
  }
}
