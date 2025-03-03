package org.idp.server.core.oauth.token;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.idp.server.core.basic.date.SystemDateTime;
import org.idp.server.core.basic.jose.JoseInvalidException;
import org.idp.server.core.basic.jose.JsonWebKeyInvalidException;
import org.idp.server.core.basic.jose.JsonWebSignature;
import org.idp.server.core.basic.jose.JsonWebSignatureFactory;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ConfigurationInvalidException;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.oauth.clientcredentials.ClientCredentials;
import org.idp.server.core.oauth.grant.AuthorizationGrant;
import org.idp.server.core.oauth.mtls.ClientCertification;
import org.idp.server.core.oauth.mtls.ClientCertificationThumbprint;
import org.idp.server.core.oauth.mtls.ClientCertificationThumbprintCalculator;
import org.idp.server.core.type.extension.CreatedAt;
import org.idp.server.core.type.extension.ExpiredAt;
import org.idp.server.core.type.oauth.AccessTokenEntity;
import org.idp.server.core.type.oauth.ExpiresIn;
import org.idp.server.core.type.oauth.TokenType;

public interface AccessTokenCreatable {

  default AccessToken createAccessToken(
      AuthorizationGrant authorizationGrant,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration,
      ClientCredentials clientCredentials) {
    try {
      LocalDateTime localDateTime = SystemDateTime.now();
      CreatedAt createdAt = new CreatedAt(localDateTime);
      long accessTokenDuration = serverConfiguration.accessTokenDuration();
      ExpiresIn expiresIn = new ExpiresIn(accessTokenDuration);
      ExpiredAt expiredAt = new ExpiredAt(localDateTime.plusSeconds(accessTokenDuration));

      AccessTokenPayloadBuilder payloadBuilder = new AccessTokenPayloadBuilder();
      payloadBuilder.add(serverConfiguration.tokenIssuer());
      payloadBuilder.add(authorizationGrant.subject());
      payloadBuilder.add(authorizationGrant.clientId());
      payloadBuilder.add(authorizationGrant.scopes());
      payloadBuilder.add(authorizationGrant.customProperties());
      payloadBuilder.add(authorizationGrant.authorizationDetails());
      payloadBuilder.add(createdAt);
      payloadBuilder.add(expiredAt);
      payloadBuilder.addJti(UUID.randomUUID().toString());

      ClientCertificationThumbprint thumbprint = new ClientCertificationThumbprint();
      if (clientCredentials.isTlsClientAuthOrSelfSignedTlsClientAuth()
          && serverConfiguration.isTlsClientCertificateBoundAccessTokens()
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
              serverConfiguration.jwks(),
              serverConfiguration.tokenSignedKeyId());
      AccessTokenEntity accessTokenEntity = new AccessTokenEntity(jsonWebSignature.serialize());

      return new AccessToken(
          serverConfiguration.tokenIssuer(),
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
