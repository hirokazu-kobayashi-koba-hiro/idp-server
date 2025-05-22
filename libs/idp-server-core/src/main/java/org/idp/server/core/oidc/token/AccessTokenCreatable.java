/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.token;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.idp.server.basic.jose.JoseInvalidException;
import org.idp.server.basic.jose.JsonWebKeyInvalidException;
import org.idp.server.basic.jose.JsonWebSignature;
import org.idp.server.basic.jose.JsonWebSignatureFactory;
import org.idp.server.basic.type.extension.CreatedAt;
import org.idp.server.basic.type.extension.ExpiredAt;
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
import org.idp.server.platform.date.SystemDateTime;

public interface AccessTokenCreatable {

  default AccessToken createAccessToken(
      AuthorizationGrant authorizationGrant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration,
      ClientCredentials clientCredentials) {
    try {
      LocalDateTime localDateTime = SystemDateTime.now();
      CreatedAt createdAt = new CreatedAt(localDateTime);
      long accessTokenDuration = authorizationServerConfiguration.accessTokenDuration();
      ExpiresIn expiresIn = new ExpiresIn(accessTokenDuration);
      ExpiredAt expiredAt = new ExpiredAt(localDateTime.plusSeconds(accessTokenDuration));

      AccessTokenPayloadBuilder payloadBuilder = new AccessTokenPayloadBuilder();
      payloadBuilder.add(authorizationServerConfiguration.tokenIssuer());
      payloadBuilder.add(authorizationGrant.subject());
      payloadBuilder.add(authorizationGrant.requestedClientId());
      payloadBuilder.add(authorizationGrant.scopes());
      payloadBuilder.add(authorizationGrant.customProperties());
      payloadBuilder.add(authorizationGrant.authorizationDetails());
      payloadBuilder.add(createdAt);
      payloadBuilder.add(expiredAt);
      payloadBuilder.addJti(UUID.randomUUID().toString());

      ClientCertificationThumbprint thumbprint = new ClientCertificationThumbprint();
      if (clientCredentials.isTlsClientAuthOrSelfSignedTlsClientAuth()
          && authorizationServerConfiguration.isTlsClientCertificateBoundAccessTokens()
          && clientConfiguration.isTlsClientCertificateBoundAccessTokens()) {
        ClientCertification clientCertification = clientCredentials.clientCertification();
        ClientCertificationThumbprintCalculator calculator =
            new ClientCertificationThumbprintCalculator(clientCertification);
        thumbprint = calculator.calculate();
        payloadBuilder.add(thumbprint);
      }

      Map<String, Object> accessTokenPayload = payloadBuilder.build();
      JsonWebSignatureFactory jsonWebSignatureFactory = new JsonWebSignatureFactory();
      JsonWebSignature jsonWebSignature =
          jsonWebSignatureFactory.createWithAsymmetricKey(
              accessTokenPayload,
              Map.of(),
              authorizationServerConfiguration.jwks(),
              authorizationServerConfiguration.tokenSignedKeyId());
      AccessTokenEntity accessTokenEntity = new AccessTokenEntity(jsonWebSignature.serialize());

      return new AccessToken(
          authorizationGrant.tenantIdentifier(),
          authorizationServerConfiguration.tokenIssuer(),
          TokenType.Bearer,
          accessTokenEntity,
          authorizationGrant,
          thumbprint,
          createdAt,
          expiresIn,
          expiredAt);
    } catch (JoseInvalidException | JsonWebKeyInvalidException exception) {
      throw new ConfigurationInvalidException(exception);
    }
  }
}
