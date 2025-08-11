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

package org.idp.server.core.openid.userinfo.handler;

import java.util.Map;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.type.oauth.AccessTokenEntity;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.repository.OAuthTokenQueryRepository;
import org.idp.server.core.openid.token.tokenintrospection.exception.TokenInvalidException;
import org.idp.server.core.openid.userinfo.UserinfoClaimsCreator;
import org.idp.server.core.openid.userinfo.UserinfoResponse;
import org.idp.server.core.openid.userinfo.handler.io.UserinfoRequest;
import org.idp.server.core.openid.userinfo.handler.io.UserinfoRequestResponse;
import org.idp.server.core.openid.userinfo.handler.io.UserinfoRequestStatus;
import org.idp.server.core.openid.userinfo.validator.UserinfoValidator;
import org.idp.server.core.openid.userinfo.verifier.UserinfoVerifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class UserinfoHandler {

  OAuthTokenQueryRepository oAuthTokenQueryRepository;
  AuthorizationServerConfigurationQueryRepository authorizationServerConfigurationQueryRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;

  public UserinfoHandler(
      OAuthTokenQueryRepository oAuthTokenQueryRepository,
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository) {
    this.oAuthTokenQueryRepository = oAuthTokenQueryRepository;
    this.authorizationServerConfigurationQueryRepository =
        authorizationServerConfigurationQueryRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
  }

  public UserinfoRequestResponse handle(UserinfoRequest request, UserinfoDelegate delegate) {
    AccessTokenEntity accessTokenEntity = request.toAccessToken();
    Tenant tenant = request.tenant();

    UserinfoValidator validator = new UserinfoValidator(request);
    validator.validate();

    OAuthToken oAuthToken = oAuthTokenQueryRepository.find(tenant, accessTokenEntity);

    if (!oAuthToken.exists()) {
      throw new TokenInvalidException("not found token");
    }

    User user = delegate.findUser(tenant, oAuthToken.subject());
    UserinfoVerifier verifier = new UserinfoVerifier(oAuthToken, request.toClientCert(), user);
    verifier.verify();

    UserinfoClaimsCreator claimsCreator =
        new UserinfoClaimsCreator(user, oAuthToken.authorizationGrant());
    Map<String, Object> claims = claimsCreator.createClaims();
    UserinfoResponse userinfoResponse = new UserinfoResponse(user, claims);
    return new UserinfoRequestResponse(UserinfoRequestStatus.OK, oAuthToken, userinfoResponse);
  }
}
