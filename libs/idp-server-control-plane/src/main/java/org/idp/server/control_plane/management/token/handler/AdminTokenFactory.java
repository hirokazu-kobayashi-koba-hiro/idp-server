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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.idp.server.control_plane.management.exception.InvalidRequestException;
import org.idp.server.control_plane.management.token.io.TokenCreateRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.type.extension.CreatedAt;
import org.idp.server.core.openid.oauth.type.extension.ExpiresAt;
import org.idp.server.core.openid.oauth.type.oauth.AccessTokenEntity;
import org.idp.server.core.openid.oauth.type.oauth.RefreshTokenEntity;
import org.idp.server.core.openid.token.RefreshToken;
import org.idp.server.platform.jose.JsonWebSignature;
import org.idp.server.platform.jose.JsonWebSignatureFactory;
import org.idp.server.platform.random.RandomStringGenerator;

/**
 * Factory for creating tokens via management API.
 *
 * <p>This class is independent from the Application Plane OAuth flow (AccessTokenCreator / plugin
 * pipeline). All methods are pure functions with no repository dependencies, making them easily
 * testable.
 */
public class AdminTokenFactory {

  public boolean resolveUseJwt(
      TokenCreateRequest request, AuthorizationServerConfiguration serverConfig) {
    if (request.hasTokenFormat()) {
      return request.isJwtFormat();
    }
    return !serverConfig.isIdentifierAccessTokenType();
  }

  public long resolveAccessTokenDuration(
      TokenCreateRequest request,
      ClientConfiguration clientConfig,
      AuthorizationServerConfiguration serverConfig) {
    if (request.hasAccessTokenDuration()) {
      return request.accessTokenDuration();
    }
    if (clientConfig.hasAccessTokenDuration()) {
      return clientConfig.accessTokenDuration();
    }
    return serverConfig.accessTokenDuration();
  }

  public long resolveRefreshTokenDuration(
      TokenCreateRequest request,
      ClientConfiguration clientConfig,
      AuthorizationServerConfiguration serverConfig) {
    if (request.hasRefreshTokenDuration()) {
      return request.refreshTokenDuration();
    }
    if (clientConfig.hasRefreshTokenDuration()) {
      return clientConfig.refreshTokenDuration();
    }
    return serverConfig.refreshTokenDuration();
  }

  public boolean shouldCreateRefreshToken(TokenCreateRequest request) {
    if (request.hasRefreshTokenDuration() && request.refreshTokenDuration() == 0) {
      return false;
    }
    return request.hasUserId();
  }

  public Map<String, Object> buildJwtPayload(
      TokenCreateRequest request,
      AuthorizationServerConfiguration serverConfig,
      String clientId,
      String scopes,
      User user,
      CreatedAt createdAt,
      ExpiresAt expiresAt) {

    Map<String, Object> payload = new HashMap<>();

    // Custom claims first (can be overwritten by standard claims)
    if (request.hasCustomClaims()) {
      payload.putAll(request.customClaims());
    }

    // Standard claims (override custom claims for security)
    payload.put("iss", serverConfig.tokenIssuer().value());
    payload.put("client_id", clientId);
    payload.put("scope", scopes);
    payload.put("jti", UUID.randomUUID().toString());
    payload.put("iat", createdAt.toEpochSecondWithUtc());
    payload.put("exp", expiresAt.toEpochSecondWithUtc());
    if (user.exists()) {
      payload.put("sub", user.sub());
    }

    return payload;
  }

  public AccessTokenEntity createAccessTokenEntity(
      boolean useJwt,
      Map<String, Object> jwtPayload,
      AuthorizationServerConfiguration serverConfig) {

    if (!useJwt) {
      RandomStringGenerator generator = new RandomStringGenerator(32);
      return new AccessTokenEntity(generator.generate());
    }

    try {
      JsonWebSignatureFactory factory = new JsonWebSignatureFactory();
      JsonWebSignature jws =
          factory.createWithAsymmetricKey(
              jwtPayload,
              Map.of("typ", "at+jwt"),
              serverConfig.jwks(),
              serverConfig.tokenSignedKeyId());
      return new AccessTokenEntity(jws.serialize());
    } catch (Exception e) {
      throw new InvalidRequestException("Failed to create JWT access token: " + e.getMessage());
    }
  }

  public RefreshToken createRefreshToken(long duration, LocalDateTime now) {
    RandomStringGenerator generator = new RandomStringGenerator(32);
    RefreshTokenEntity refreshTokenEntity = new RefreshTokenEntity(generator.generate());
    CreatedAt createdAt = new CreatedAt(now);
    ExpiresAt expiresAt = new ExpiresAt(now.plusSeconds(duration));
    return new RefreshToken(refreshTokenEntity, createdAt, expiresAt);
  }

  public Map<String, Object> buildResponse(
      String tokenId,
      String accessTokenValue,
      String tokenType,
      long expiresIn,
      String refreshTokenValue,
      String scopes) {

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("id", tokenId);
    response.put("access_token", accessTokenValue);
    response.put("token_type", tokenType);
    response.put("expires_in", expiresIn);
    if (refreshTokenValue != null) {
      response.put("refresh_token", refreshTokenValue);
    }
    response.put("scopes", scopes);
    return response;
  }
}
