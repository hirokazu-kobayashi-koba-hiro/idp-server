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

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrant;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrantBuilder;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.device.AuthenticationDevice;
import org.idp.server.core.openid.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.core.openid.identity.device.authentication.DeviceAuthenticationVerifier;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.AvailableFederation;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.type.extension.CustomProperties;
import org.idp.server.core.openid.oauth.type.oauth.GrantType;
import org.idp.server.core.openid.oauth.type.oauth.JwtBearerAssertion;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.core.openid.token.*;
import org.idp.server.core.openid.token.exception.TokenBadRequestException;
import org.idp.server.core.openid.token.repository.OAuthTokenCommandRepository;
import org.idp.server.core.openid.token.validator.JwtBearerGrantValidator;
import org.idp.server.core.openid.token.verifier.JwtBearerGrantVerifier;
import org.idp.server.platform.http.HttpRequestExecutor;
import org.idp.server.platform.http.HttpRequestResult;
import org.idp.server.platform.jose.JoseInvalidException;
import org.idp.server.platform.jose.JsonWebSignature;
import org.idp.server.platform.jose.JsonWebTokenClaims;
import org.idp.server.platform.jose.JwtCredential;
import org.idp.server.platform.jose.JwtSignatureVerifier;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * JwtBearerGrantService
 *
 * <p>Implements RFC 7523 JWT Bearer Grant (urn:ietf:params:oauth:grant-type:jwt-bearer).
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7523">RFC 7523</a>
 */
public class JwtBearerGrantService implements OAuthTokenCreationService, RefreshTokenCreatable {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(JwtBearerGrantService.class);

  OAuthTokenCommandRepository oAuthTokenCommandRepository;
  DeviceAuthenticationVerifier deviceAuthenticationVerifier;
  HttpRequestExecutor httpRequestExecutor;
  AccessTokenCreator accessTokenCreator;

  public JwtBearerGrantService(
      OAuthTokenCommandRepository oAuthTokenCommandRepository,
      HttpRequestExecutor httpRequestExecutor) {
    this.oAuthTokenCommandRepository = oAuthTokenCommandRepository;
    this.deviceAuthenticationVerifier = new DeviceAuthenticationVerifier();
    this.httpRequestExecutor = httpRequestExecutor;
    this.accessTokenCreator = AccessTokenCreator.getInstance();
  }

  @Override
  public GrantType grantType() {
    return GrantType.jwt_bearer;
  }

  @Override
  public OAuthToken create(TokenRequestContext context, ClientCredentials clientCredentials) {
    JwtBearerGrantValidator validator = new JwtBearerGrantValidator(context);
    validator.validate();

    Tenant tenant = context.tenant();
    AuthorizationServerConfiguration serverConfiguration = context.serverConfiguration();
    ClientConfiguration clientConfiguration = context.clientConfiguration();

    JwtBearerAssertion assertion = context.assertion();

    try {
      JsonWebSignature jws = assertion.parse();
      JsonWebTokenClaims claims = jws.claims();
      String issuer = claims.getIss();
      String subject = claims.getSub();

      AvailableFederation federation = findFederation(context, issuer);
      // Default: device_id for device federations, sub for external IdP
      String defaultMapping = federation.isDeviceType() ? "device_id" : "sub";
      String subjectClaimMapping =
          federation.hasSubjectClaimMapping() ? federation.subjectClaimMapping() : defaultMapping;

      verifySignature(context, assertion, jws, federation);

      String expectedAudience = serverConfiguration.tokenIssuer().value();
      JwtBearerGrantVerifier verifier = new JwtBearerGrantVerifier(claims, expectedAudience);
      verifier.verify();

      JwtBearerUserFindingDelegate delegate = context.jwtBearerUserFindingDelegate();
      String providerId = federation.resolveProviderId();
      User user = delegate.findUser(tenant, providerId, subject, subjectClaimMapping);

      if (!user.exists()) {
        throw new TokenBadRequestException(
            "invalid_grant", "User not found for the given assertion subject");
      }

      Set<String> filteredScopes =
          clientConfiguration.filteredScope(context.scopes().toStringValues());
      Scopes scopes = new Scopes(filteredScopes);

      CustomProperties customProperties = context.customProperties();
      AuthorizationGrant authorizationGrant =
          new AuthorizationGrantBuilder(
                  context.tenantIdentifier(),
                  context.requestedClientId(),
                  GrantType.jwt_bearer,
                  scopes)
              .add(customProperties)
              .add(clientConfiguration.clientAttributes())
              .add(user)
              .build();

      AccessToken accessToken =
          accessTokenCreator.create(
              authorizationGrant, serverConfiguration, clientConfiguration, clientCredentials);

      RefreshToken refreshToken = createRefreshToken(serverConfiguration, clientConfiguration);

      OAuthTokenBuilder tokenBuilder =
          new OAuthTokenBuilder(new OAuthTokenIdentifier(UUID.randomUUID().toString()))
              .add(accessToken)
              .add(refreshToken);

      OAuthToken oAuthToken = tokenBuilder.build();
      oAuthTokenCommandRepository.register(tenant, oAuthToken);

      return oAuthToken;

    } catch (TokenBadRequestException e) {
      throw e;
    } catch (JoseInvalidException e) {
      throw new TokenBadRequestException(
          "invalid_grant", "Invalid JWT assertion: " + e.getMessage());
    } catch (IllegalArgumentException e) {
      throw new TokenBadRequestException(
          "invalid_request", "Invalid request parameter: " + e.getMessage());
    } catch (RuntimeException e) {
      log.error("Unexpected error processing JWT Bearer assertion", e);
      throw new TokenBadRequestException(
          "server_error", "Unexpected error processing JWT Bearer assertion");
    }
  }

  private AvailableFederation findFederation(TokenRequestContext context, String issuer)
      throws JoseInvalidException {
    JwtBearerAssertion assertion = context.assertion();

    if (assertion.isDeviceIssuer()) {
      Optional<AvailableFederation> deviceFederation = context.findDeviceFederation();
      if (deviceFederation.isEmpty()) {
        throw new TokenBadRequestException(
            "invalid_grant", "Device federation not configured for this client");
      }
      return deviceFederation.get();
    }

    Optional<AvailableFederation> federation = context.findAvailableFederationByIssuer(issuer);
    if (federation.isEmpty()) {
      throw new TokenBadRequestException(
          "invalid_grant", String.format("Issuer '%s' is not trusted for this client", issuer));
    }
    return federation.get();
  }

  private void verifySignature(
      TokenRequestContext context,
      JwtBearerAssertion assertion,
      JsonWebSignature jws,
      AvailableFederation federation) {
    try {
      if (federation.isDeviceType()) {
        verifyDeviceSignature(context, assertion, jws);
      } else {
        verifyExternalIdpSignature(jws, federation);
      }
    } catch (TokenBadRequestException e) {
      throw e;
    } catch (Exception e) {
      throw new TokenBadRequestException(
          "invalid_grant", "JWT signature verification failed: " + e.getMessage());
    }
  }

  private void verifyDeviceSignature(
      TokenRequestContext context, JwtBearerAssertion assertion, JsonWebSignature jws)
      throws JoseInvalidException {
    String deviceId = assertion.extractDeviceId();
    AuthenticationDeviceIdentifier deviceIdentifier = new AuthenticationDeviceIdentifier(deviceId);

    JwtBearerUserFindingDelegate delegate = context.jwtBearerUserFindingDelegate();
    AuthenticationDevice device =
        delegate.findAuthenticationDevice(context.tenant(), deviceIdentifier);

    if (!device.exists()) {
      throw new JoseInvalidException(
          String.format("Device '%s' not found", deviceIdentifier.value()));
    }

    deviceAuthenticationVerifier.verifySignature(device, jws);
  }

  private void verifyExternalIdpSignature(JsonWebSignature jws, AvailableFederation federation) {
    try {
      String jwks = resolveJwks(federation);

      JwtCredential jwtCredential = JwtCredential.asymmetric(jwks);
      JwtSignatureVerifier signatureVerifier = new JwtSignatureVerifier();
      signatureVerifier.verify(jws, jwtCredential);
    } catch (TokenBadRequestException e) {
      throw e;
    } catch (Exception e) {
      throw new TokenBadRequestException(
          "invalid_grant", "External IdP signature verification failed: " + e.getMessage());
    }
  }

  private String resolveJwks(AvailableFederation federation) {
    if (federation.hasJwks()) {
      return federation.jwks();
    }
    if (federation.hasJwksUri()) {
      return fetchJwks(federation.jwksUri());
    }
    throw new TokenBadRequestException(
        "invalid_grant",
        String.format(
            "Federation '%s' does not have JWKS configured (neither jwks nor jwks_uri)",
            federation.issuer()));
  }

  private String fetchJwks(String jwksUri) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(jwksUri))
              .GET()
              .header("Accept", "application/json")
              .build();

      HttpRequestResult result = httpRequestExecutor.execute(request);

      if (result.isClientError() || result.isServerError()) {
        throw new TokenBadRequestException(
            "invalid_grant",
            String.format("Failed to fetch JWKS from '%s': HTTP %d", jwksUri, result.statusCode()));
      }

      return result.body().toString();
    } catch (TokenBadRequestException e) {
      throw e;
    } catch (Exception e) {
      throw new TokenBadRequestException(
          "invalid_grant",
          String.format("Failed to fetch JWKS from '%s': %s", jwksUri, e.getMessage()));
    }
  }
}
