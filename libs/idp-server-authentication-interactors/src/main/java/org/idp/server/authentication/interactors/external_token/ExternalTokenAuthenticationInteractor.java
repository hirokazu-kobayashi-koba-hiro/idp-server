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
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.http.*;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;

public class ExternalTokenAuthenticationInteractor implements AuthenticationInteractor {
  AuthenticationConfigurationQueryRepository configurationRepository;
  HttpRequestExecutor httpRequestExecutor;

  public ExternalTokenAuthenticationInteractor(
      AuthenticationConfigurationQueryRepository configurationRepository) {
    this.configurationRepository = configurationRepository;
    this.httpRequestExecutor = new HttpRequestExecutor(HttpClientFactory.defaultClient());
  }

  @Override
  public AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthenticationTransaction transaction,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      UserQueryRepository userQueryRepository) {

    ExternalTokenAuthenticationConfiguration configuration =
        configurationRepository.get(
            tenant, "external-token", ExternalTokenAuthenticationConfiguration.class);

    ExternalTokenAuthenticationDetailConfiguration userinfoConfig =
        configuration.userinfoDetailConfig();

    HttpRequestResult userinfoResult = execute(request, userinfoConfig);

    if (userinfoResult.isClientError()) {
      return AuthenticationInteractionRequestResult.clientError(
          userinfoResult.toMap(),
          type,
          DefaultSecurityEventType.external_token_authentication_failure);
    }

    if (userinfoResult.isServerError()) {
      return AuthenticationInteractionRequestResult.serverError(
          userinfoResult.toMap(),
          type,
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
            tenant, configuration.providerName(), user.providerUserId());
    if (exsitingUser.exists()) {
      user.setSub(exsitingUser.sub());
    } else {
      user.setSub(UUID.randomUUID().toString());
    }

    Authentication authentication =
        new Authentication()
            .setTime(SystemDateTime.now())
            .addMethods(new ArrayList<>(List.of("token")))
            .addAcrValues(List.of("urn:mace:incommon:iap:silver"));

    Map<String, Object> result = new HashMap<>();
    result.put("user", user.toMap());
    result.put("authentication", authentication.toMap());

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        user,
        authentication,
        result,
        DefaultSecurityEventType.external_token_authentication_success);
  }

  // TODO to be more simply
  private HttpRequestResult execute(
      AuthenticationInteractionRequest request,
      ExternalTokenAuthenticationDetailConfiguration userinfoConfig) {

    if (userinfoConfig.isGetHttpMethod()) {
      HttpRequestUrl httpRequestUrl = userinfoConfig.httpRequestUrl();
      HttpRequestStaticHeaders httpRequestStaticHeaders = userinfoConfig.httpRequestHeaders();
      HttpRequestMappingRules queryMappingRules = userinfoConfig.httpRequestQueryMappingRules();
      HttpRequestBaseParams httpRequestBaseParams = new HttpRequestBaseParams(request.toMap());
      return httpRequestExecutor.getWithDynamicQueryMapping(
          httpRequestUrl, httpRequestStaticHeaders, queryMappingRules, httpRequestBaseParams);
    }

    if (userinfoConfig.hasHmacAuthentication()) {
      return httpRequestExecutor.execute(
          userinfoConfig.httpRequestUrl(),
          userinfoConfig.httpMethod(),
          userinfoConfig.hmacAuthentication(),
          userinfoConfig.httpRequestHeaders(),
          new HttpRequestBaseParams(request.toMap()),
          userinfoConfig.httpRequestDynamicBodyKeys(),
          userinfoConfig.httpRequestStaticBody());
    }

    if (userinfoConfig.hasDynamicBodyKeys()) {

      return httpRequestExecutor.execute(
          userinfoConfig.httpRequestUrl(),
          userinfoConfig.httpMethod(),
          userinfoConfig.httpRequestHeaders(),
          new HttpRequestBaseParams(request.toMap()),
          userinfoConfig.httpRequestDynamicBodyKeys(),
          userinfoConfig.httpRequestStaticBody());
    }

    return httpRequestExecutor.executeWithDynamicMapping(
        userinfoConfig.httpRequestUrl(),
        userinfoConfig.httpMethod(),
        userinfoConfig.httpRequestHeaders(),
        userinfoConfig.httpRequestHeaderMappingRules(),
        userinfoConfig.httpRequestHeaderMappingRules(),
        new HttpRequestBaseParams(request.toMap()),
        userinfoConfig.httpRequestStaticBody());
  }
}
