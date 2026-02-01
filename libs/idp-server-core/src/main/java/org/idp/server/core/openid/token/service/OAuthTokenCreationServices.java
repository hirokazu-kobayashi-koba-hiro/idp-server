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

package org.idp.server.core.openid.token.service;

import static org.idp.server.core.openid.oauth.type.oauth.GrantType.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.openid.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.openid.oauth.repository.AuthorizationCodeGrantRepository;
import org.idp.server.core.openid.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.core.openid.oauth.type.oauth.GrantType;
import org.idp.server.core.openid.token.exception.TokenUnSupportedGrantException;
import org.idp.server.core.openid.token.repository.OAuthTokenCommandRepository;
import org.idp.server.core.openid.token.repository.OAuthTokenQueryRepository;

public class OAuthTokenCreationServices {

  Map<GrantType, OAuthTokenCreationService> values = new HashMap<>();

  public OAuthTokenCreationServices(
      AuthorizationRequestRepository authorizationRequestRepository,
      AuthorizationCodeGrantRepository authorizationCodeGrantRepository,
      OAuthTokenCommandRepository oAuthTokenCommandRepository,
      OAuthTokenQueryRepository oAuthTokenQueryRepository,
      AuthorizationGrantedRepository authorizationGrantedRepository,
      Map<GrantType, OAuthTokenCreationService> extensionOAuthTokenCreationServices) {
    values.put(
        authorization_code,
        new AuthorizationCodeGrantService(
            authorizationRequestRepository,
            oAuthTokenCommandRepository,
            authorizationCodeGrantRepository));
    values.put(
        refresh_token,
        new RefreshTokenGrantService(oAuthTokenCommandRepository, oAuthTokenQueryRepository));
    values.put(
        password,
        new ResourceOwnerPasswordCredentialsGrantService(
            oAuthTokenCommandRepository, authorizationGrantedRepository));
    values.put(client_credentials, new ClientCredentialsGrantService(oAuthTokenCommandRepository));
    values.putAll(extensionOAuthTokenCreationServices);
  }

  public OAuthTokenCreationService get(GrantType grantType) {
    OAuthTokenCreationService oAuthTokenCreationService = values.get(grantType);
    if (Objects.isNull(oAuthTokenCreationService)) {
      throw new TokenUnSupportedGrantException(
          String.format("unsupported grant_type (%s)", grantType.name()));
    }
    return oAuthTokenCreationService;
  }
}
