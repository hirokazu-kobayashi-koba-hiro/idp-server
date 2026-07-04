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

package org.idp.server.core.openid.token.handler.tokenintrospection;

import java.util.Map;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserStatus;
import org.idp.server.core.openid.oauth.clientauthenticator.ClientAuthenticationHandler;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.type.oauth.AccessTokenEntity;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.TokenUserFindingDelegate;
import org.idp.server.core.openid.token.handler.tokenintrospection.io.TokenIntrospectionRequest;
import org.idp.server.core.openid.token.handler.tokenintrospection.io.TokenIntrospectionRequestStatus;
import org.idp.server.core.openid.token.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.idp.server.core.openid.token.repository.OAuthTokenQueryRepository;
import org.idp.server.core.openid.token.tokenintrospection.TokenIntrospectionContentsCreator;
import org.idp.server.core.openid.token.tokenintrospection.TokenIntrospectionRequestContext;
import org.idp.server.core.openid.token.tokenintrospection.TokenIntrospectionRequestParameters;
import org.idp.server.core.openid.token.tokenintrospection.validator.TokenIntrospectionValidator;
import org.idp.server.core.openid.token.tokenintrospection.verifier.TokenIntrospectionClientAuthenticationVerifier;
import org.idp.server.core.openid.token.tokenintrospection.verifier.TokenIntrospectionUserVerifier;
import org.idp.server.core.openid.token.tokenintrospection.verifier.TokenIntrospectionVerifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class TokenIntrospectionHandler {

  OAuthTokenQueryRepository oAuthTokenQueryRepository;
  AuthorizationServerConfigurationQueryRepository authorizationServerConfigurationQueryRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;
  ClientAuthenticationHandler clientAuthenticationHandler;

  public TokenIntrospectionHandler(
      OAuthTokenQueryRepository oAuthTokenQueryRepository,
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository) {
    this.oAuthTokenQueryRepository = oAuthTokenQueryRepository;
    this.authorizationServerConfigurationQueryRepository =
        authorizationServerConfigurationQueryRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
    this.clientAuthenticationHandler = new ClientAuthenticationHandler();
  }

  public TokenIntrospectionResponse handle(
      TokenIntrospectionRequest request, TokenUserFindingDelegate delegate) {
    TokenIntrospectionValidator validator = new TokenIntrospectionValidator(request.toParameters());
    validator.validate();

    Tenant tenant = request.tenant();
    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationQueryRepository.get(tenant);
    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.get(tenant, request.clientId());

    TokenIntrospectionRequestContext introspectionRequestContext =
        new TokenIntrospectionRequestContext(
            request.clientSecretBasic(),
            request.toClientCert(),
            request.toParameters(),
            authorizationServerConfiguration,
            clientConfiguration);
    // #1707 (RFC 7662 §2.1): introspection requires client authentication; reject public clients
    // (token_endpoint_auth_method=none) before the shared handler lets `none` through unchecked.
    new TokenIntrospectionClientAuthenticationVerifier(
            clientConfiguration.clientAuthenticationType())
        .verify();
    clientAuthenticationHandler.authenticate(introspectionRequestContext);

    OAuthToken oAuthToken = find(request);
    TokenIntrospectionVerifier verifier = new TokenIntrospectionVerifier(oAuthToken);
    TokenIntrospectionRequestStatus verifiedStatus = verifier.verify();

    if (!verifiedStatus.isOK()) {
      Map<String, Object> contents = TokenIntrospectionContentsCreator.createFailureContents();
      return new TokenIntrospectionResponse(verifiedStatus, oAuthToken, contents);
    }

    if (!oAuthToken.isClientCredentialsGrant()) {
      UserStatus status = delegate.findUserStatus(tenant, oAuthToken.subject());
      User minimal = new User();
      if (status != null) {
        minimal.setSub(oAuthToken.subject().value());
        minimal.setStatus(status);
      }
      TokenIntrospectionUserVerifier userVerifier = new TokenIntrospectionUserVerifier(minimal);
      userVerifier.verify();
    }

    Map<String, Object> contents =
        TokenIntrospectionContentsCreator.createSuccessContents(oAuthToken);

    return new TokenIntrospectionResponse(verifiedStatus, oAuthToken, contents);
  }

  OAuthToken find(TokenIntrospectionRequest request) {
    TokenIntrospectionRequestParameters parameters = request.toParameters();
    AccessTokenEntity accessTokenEntity = parameters.accessToken();
    Tenant tenant = request.tenant();
    // #1707: introspect access tokens only. A refresh-token fallback would let a refresh token
    // presented as a Bearer token be returned active with the access token's claims (token type
    // confusion); introspecting a refresh token now yields active:false.
    return oAuthTokenQueryRepository.find(tenant, accessTokenEntity);
  }
}
