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

package org.idp.server.core.oidc.token;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.idp.server.basic.jose.JoseInvalidException;
import org.idp.server.basic.jose.JsonWebKeyInvalidException;
import org.idp.server.basic.jose.JsonWebSignature;
import org.idp.server.basic.jose.JsonWebSignatureFactory;
import org.idp.server.basic.random.RandomStringGenerator;
import org.idp.server.basic.type.extension.CreatedAt;
import org.idp.server.basic.type.extension.ExpiresAt;
import org.idp.server.basic.type.oauth.AccessTokenEntity;
import org.idp.server.basic.type.oauth.ExpiresIn;
import org.idp.server.basic.type.oauth.TokenType;
import org.idp.server.core.oidc.clientcredentials.ClientCredentials;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.configuration.exception.ConfigurationInvalidException;
import org.idp.server.core.oidc.grant.AuthorizationGrant;
import org.idp.server.core.oidc.mtls.ClientCertification;
import org.idp.server.core.oidc.mtls.ClientCertificationThumbprint;
import org.idp.server.core.oidc.mtls.ClientCertificationThumbprintCalculator;
import org.idp.server.core.oidc.token.plugin.AccessTokenCustomClaimsCreators;
import org.idp.server.platform.date.SystemDateTime;

public class AccessTokenCreator {

  public static final AccessTokenCreator INSTANCE = new AccessTokenCreator();
  AccessTokenCustomClaimsCreators customClaimsCreators;

  public static AccessTokenCreator getInstance() {
    return INSTANCE;
  }

  private AccessTokenCreator() {
    this.customClaimsCreators = new AccessTokenCustomClaimsCreators();
  }

  public AccessToken create(
      AuthorizationGrant authorizationGrant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration,
      ClientCredentials clientCredentials) {
    try {
      LocalDateTime localDateTime = SystemDateTime.now();
      CreatedAt createdAt = new CreatedAt(localDateTime);

      long accessTokenDuration =
          clientConfiguration.hasAccessTokenDuration()
              ? clientConfiguration.accessTokenDuration()
              : authorizationServerConfiguration.accessTokenDuration();

      ExpiresIn expiresIn = new ExpiresIn(accessTokenDuration);
      ExpiresAt expiresAt = new ExpiresAt(localDateTime.plusSeconds(accessTokenDuration));

      return issueAccessToken(
          authorizationGrant,
          authorizationServerConfiguration,
          clientConfiguration,
          clientCredentials,
          createdAt,
          expiresIn,
          expiresAt);
    } catch (JoseInvalidException | JsonWebKeyInvalidException exception) {
      throw new ConfigurationInvalidException(exception);
    }
  }

  public AccessToken refresh(
      AccessToken oldAccessToken,
      AuthorizationGrant authorizationGrant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration,
      ClientCredentials clientCredentials) {
    try {
      LocalDateTime localDateTime = SystemDateTime.now();
      CreatedAt createdAt = new CreatedAt(localDateTime);

      if (authorizationServerConfiguration.isExtendsAccessTokenStrategy()) {

        long accessTokenDuration =
            clientConfiguration.hasAccessTokenDuration()
                ? clientConfiguration.accessTokenDuration()
                : authorizationServerConfiguration.accessTokenDuration();

        ExpiresIn expiresIn = new ExpiresIn(accessTokenDuration);
        ExpiresAt expiresAt = new ExpiresAt(localDateTime.plusSeconds(accessTokenDuration));
        return issueAccessToken(
            authorizationGrant,
            authorizationServerConfiguration,
            clientConfiguration,
            clientCredentials,
            createdAt,
            expiresIn,
            expiresAt);
      }

      ExpiresIn expiresIn = oldAccessToken.expiresIn();
      ExpiresAt expiresAt = oldAccessToken.expiresAt();

      return issueAccessToken(
          authorizationGrant,
          authorizationServerConfiguration,
          clientConfiguration,
          clientCredentials,
          createdAt,
          expiresIn,
          expiresAt);
    } catch (JoseInvalidException | JsonWebKeyInvalidException exception) {
      throw new ConfigurationInvalidException(exception);
    }
  }

  private AccessToken issueAccessToken(
      AuthorizationGrant authorizationGrant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration,
      ClientCredentials clientCredentials,
      CreatedAt createdAt,
      ExpiresIn expiresIn,
      ExpiresAt expiresAt)
      throws JsonWebKeyInvalidException, JoseInvalidException {
    AccessTokenPayloadBuilder payloadBuilder = new AccessTokenPayloadBuilder();
    payloadBuilder.add(authorizationServerConfiguration.tokenIssuer());
    payloadBuilder.add(authorizationGrant.subject());
    payloadBuilder.add(authorizationGrant.requestedClientId());
    payloadBuilder.add(authorizationGrant.scopes());
    payloadBuilder.add(authorizationGrant.authorizationDetails());
    payloadBuilder.add(createdAt);
    payloadBuilder.add(expiresAt);
    payloadBuilder.addJti(UUID.randomUUID().toString());

    Map<String, Object> customClaims =
        customClaimsCreators.create(
            authorizationGrant,
            authorizationServerConfiguration,
            clientConfiguration,
            clientCredentials);
    payloadBuilder.addCustomClaims(customClaims);

    ClientCertificationThumbprint thumbprint =
        createClientCertificationThumbprint(
            authorizationServerConfiguration, clientConfiguration, clientCredentials);
    payloadBuilder.add(thumbprint);

    Map<String, Object> accessTokenPayload = payloadBuilder.build();
    AccessTokenEntity accessTokenEntity =
        createAccessTokenEntity(authorizationServerConfiguration, accessTokenPayload);

    return new AccessToken(
        authorizationGrant.tenantIdentifier(),
        authorizationServerConfiguration.tokenIssuer(),
        TokenType.Bearer,
        accessTokenEntity,
        authorizationGrant,
        thumbprint,
        createdAt,
        expiresIn,
        expiresAt);
  }

  private AccessTokenEntity createAccessTokenEntity(
      AuthorizationServerConfiguration authorizationServerConfiguration,
      Map<String, Object> accessTokenPayload)
      throws JsonWebKeyInvalidException, JoseInvalidException {
    if (authorizationServerConfiguration.isIdentifierAccessTokenType()) {
      RandomStringGenerator randomStringGenerator = new RandomStringGenerator(32);
      String token = randomStringGenerator.generate();
      return new AccessTokenEntity(token);
    }

    JsonWebSignatureFactory jsonWebSignatureFactory = new JsonWebSignatureFactory();
    JsonWebSignature jsonWebSignature =
        jsonWebSignatureFactory.createWithAsymmetricKey(
            accessTokenPayload,
            Map.of("typ", "at+jwt"),
            authorizationServerConfiguration.jwks(),
            authorizationServerConfiguration.tokenSignedKeyId());
    return new AccessTokenEntity(jsonWebSignature.serialize());
  }

  private ClientCertificationThumbprint createClientCertificationThumbprint(
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration,
      ClientCredentials clientCredentials) {

    if (clientCredentials.isTlsClientAuthOrSelfSignedTlsClientAuth()
        && authorizationServerConfiguration.isTlsClientCertificateBoundAccessTokens()
        && clientConfiguration.isTlsClientCertificateBoundAccessTokens()) {
      ClientCertification clientCertification = clientCredentials.clientCertification();
      ClientCertificationThumbprintCalculator calculator =
          new ClientCertificationThumbprintCalculator(clientCertification);
      return calculator.calculate();
    }

    return new ClientCertificationThumbprint();
  }
}
