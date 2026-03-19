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

package org.idp.server.control_plane.management.token.handler;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.idp.server.control_plane.management.exception.InvalidRequestException;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.token.TokenManagementContextBuilder;
import org.idp.server.control_plane.management.token.io.TokenCreateRequest;
import org.idp.server.control_plane.management.token.io.TokenManagementResponse;
import org.idp.server.control_plane.management.token.io.TokenManagementStatus;
import org.idp.server.core.openid.authentication.Authentication;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrant;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrantBuilder;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.oauth.clientauthenticator.mtls.ClientCertificationThumbprint;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.type.extension.CreatedAt;
import org.idp.server.core.openid.oauth.type.extension.ExpiresAt;
import org.idp.server.core.openid.oauth.type.oauth.*;
import org.idp.server.core.openid.token.*;
import org.idp.server.core.openid.token.repository.OAuthTokenCommandRepository;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

public class TokenCreationService implements TokenManagementService<TokenCreateRequest> {

  private final AuthorizationServerConfigurationQueryRepository serverConfigRepository;
  private final ClientConfigurationQueryRepository clientConfigRepository;
  private final UserQueryRepository userQueryRepository;
  private final OAuthTokenCommandRepository oAuthTokenCommandRepository;
  private final AdminTokenFactory adminTokenFactory;

  public TokenCreationService(
      AuthorizationServerConfigurationQueryRepository serverConfigRepository,
      ClientConfigurationQueryRepository clientConfigRepository,
      UserQueryRepository userQueryRepository,
      OAuthTokenCommandRepository oAuthTokenCommandRepository) {
    this.serverConfigRepository = serverConfigRepository;
    this.clientConfigRepository = clientConfigRepository;
    this.userQueryRepository = userQueryRepository;
    this.oAuthTokenCommandRepository = oAuthTokenCommandRepository;
    this.adminTokenFactory = new AdminTokenFactory();
  }

  @Override
  public TokenManagementResponse execute(
      TokenManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      TokenCreateRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    validateRequest(request);

    AuthorizationServerConfiguration serverConfig = serverConfigRepository.get(tenant);
    ClientConfiguration clientConfig =
        clientConfigRepository.get(tenant, new RequestedClientId(request.clientId()));

    if (!clientConfig.exists()) {
      throw new ResourceNotFoundException("Client not found: " + request.clientId());
    }

    Scopes scopes = new Scopes(clientConfig.filteredScope(request.scopes()));
    GrantType grantType = request.hasUserId() ? GrantType.password : GrantType.client_credentials;

    AuthorizationGrantBuilder grantBuilder =
        new AuthorizationGrantBuilder(
            tenant.identifier(), new RequestedClientId(request.clientId()), grantType, scopes);
    grantBuilder.add(clientConfig.clientAttributes());

    User tokenUser = new User();
    if (request.hasUserId()) {
      tokenUser = userQueryRepository.get(tenant, new UserIdentifier(request.userId()));
      if (!tokenUser.exists()) {
        throw new ResourceNotFoundException("User not found: " + request.userId());
      }
      grantBuilder.add(tokenUser);
      grantBuilder.add(new Authentication());
    }

    AuthorizationGrant authorizationGrant = grantBuilder.build();

    LocalDateTime now = SystemDateTime.now();
    CreatedAt createdAt = new CreatedAt(now);

    long accessTokenDuration =
        adminTokenFactory.resolveAccessTokenDuration(request, clientConfig, serverConfig);
    ExpiresIn expiresIn = new ExpiresIn(accessTokenDuration);
    ExpiresAt expiresAt = new ExpiresAt(now.plusSeconds(accessTokenDuration));

    boolean useJwt = adminTokenFactory.resolveUseJwt(request, serverConfig);
    Map<String, Object> jwtPayload =
        adminTokenFactory.buildJwtPayload(
            request,
            serverConfig,
            request.clientId(),
            scopes.toStringValues(),
            tokenUser,
            createdAt,
            expiresAt);

    AccessTokenEntity accessTokenEntity =
        adminTokenFactory.createAccessTokenEntity(useJwt, jwtPayload, serverConfig);

    AccessTokenCustomClaims customClaims =
        request.hasCustomClaims()
            ? new AccessTokenCustomClaims(request.customClaims())
            : new AccessTokenCustomClaims();

    AccessToken accessToken =
        new AccessToken(
            tenant.identifier(),
            serverConfig.tokenIssuer(),
            TokenType.Bearer,
            accessTokenEntity,
            authorizationGrant,
            new ClientCertificationThumbprint(),
            customClaims,
            createdAt,
            expiresIn,
            expiresAt);

    OAuthTokenBuilder tokenBuilder =
        new OAuthTokenBuilder(new OAuthTokenIdentifier(UUID.randomUUID().toString()));
    tokenBuilder.add(accessToken);

    if (adminTokenFactory.shouldCreateRefreshToken(request)) {
      long refreshDuration =
          adminTokenFactory.resolveRefreshTokenDuration(request, clientConfig, serverConfig);
      RefreshToken refreshToken = adminTokenFactory.createRefreshToken(refreshDuration, now);
      tokenBuilder.add(refreshToken);
    }

    OAuthToken oAuthToken = tokenBuilder.build();

    String refreshTokenValue =
        oAuthToken.hasRefreshToken() ? oAuthToken.refreshTokenEntity().value() : null;

    if (dryRun) {
      Map<String, Object> response =
          adminTokenFactory.buildResponse(
              oAuthToken.identifier().value(),
              oAuthToken.accessTokenEntity().value(),
              oAuthToken.tokenType().name(),
              oAuthToken.expiresIn().value(),
              refreshTokenValue,
              oAuthToken.scopes().toStringValues());
      response.put("dry_run", true);
      return new TokenManagementResponse(TokenManagementStatus.OK, response);
    }

    oAuthTokenCommandRepository.register(tenant, oAuthToken);

    Map<String, Object> response =
        adminTokenFactory.buildResponse(
            oAuthToken.identifier().value(),
            oAuthToken.accessTokenEntity().value(),
            oAuthToken.tokenType().name(),
            oAuthToken.expiresIn().value(),
            refreshTokenValue,
            oAuthToken.scopes().toStringValues());
    return new TokenManagementResponse(TokenManagementStatus.CREATED, response);
  }

  private void validateRequest(TokenCreateRequest request) {
    if (request.clientId() == null || request.clientId().isEmpty()) {
      throw new InvalidRequestException("client_id is required");
    }
    if (request.scopes() == null || request.scopes().isEmpty()) {
      throw new InvalidRequestException("scopes is required");
    }
  }
}
