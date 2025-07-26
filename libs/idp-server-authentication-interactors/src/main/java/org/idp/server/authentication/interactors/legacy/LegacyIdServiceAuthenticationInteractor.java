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

package org.idp.server.authentication.interactors.legacy;

import java.util.*;
import org.idp.server.core.oidc.authentication.*;
import org.idp.server.core.oidc.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.mapper.UserInfoMapper;
import org.idp.server.core.oidc.identity.repository.UserQueryRepository;
import org.idp.server.platform.http.*;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

public class LegacyIdServiceAuthenticationInteractor implements AuthenticationInteractor {

  AuthenticationConfigurationQueryRepository configurationRepository;
  HttpRequestExecutor httpRequestExecutor;
  LoggerWrapper log = LoggerWrapper.getLogger(LegacyIdServiceAuthenticationInteractor.class);

  public LegacyIdServiceAuthenticationInteractor(
      AuthenticationConfigurationQueryRepository configurationRepository) {
    this.configurationRepository = configurationRepository;
    this.httpRequestExecutor = new HttpRequestExecutor(HttpClientFactory.defaultClient());
  }

  @Override
  public AuthenticationInteractionType type() {
    return new AuthenticationInteractionType("legacy-authentication");
  }

  @Override
  public String method() {
    return "legacy-id-service";
  }

  @Override
  public AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthenticationTransaction transaction,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes,
      UserQueryRepository userQueryRepository) {

    log.debug("LegacyIdServiceAuthenticationInteractor called");

    LegacyIdServiceAuthenticationConfiguration configuration =
        configurationRepository.get(
            tenant, "legacy-id-service", LegacyIdServiceAuthenticationConfiguration.class);

    LegacyIdServiceAuthenticationDetailConfiguration authenticationConfig =
        configuration.authenticationDetailConfig();

    Map<String, Object> param = new HashMap<>();
    param.put("request_body", request.toMap());
    param.put("request_attributes", requestAttributes.toMap());
    HttpRequestBaseParams httpRequestBaseParams = new HttpRequestBaseParams(param);

    HttpRequestResult authenticationResult =
        httpRequestExecutor.execute(authenticationConfig, httpRequestBaseParams);

    if (authenticationResult.isClientError()) {

      return new AuthenticationInteractionRequestResult(
          AuthenticationInteractionStatus.CLIENT_ERROR,
          type,
          operationType(),
          method(),
          authenticationResult.toMap(),
          DefaultSecurityEventType.legacy_authentication_failure);
    }

    LegacyIdServiceAuthenticationDetailConfiguration userinfoConfig =
        configuration.userinfoDetailConfig();

    HttpRequestResult userinfoResult =
        httpRequestExecutor.execute(authenticationConfig, httpRequestBaseParams);
    ;

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
        DefaultSecurityEventType.legacy_authentication_success);
  }
}
