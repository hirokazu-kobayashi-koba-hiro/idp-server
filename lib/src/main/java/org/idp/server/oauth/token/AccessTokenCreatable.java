package org.idp.server.oauth.token;

import java.time.LocalDateTime;
import java.util.Map;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.basic.jose.JsonWebSignature;
import org.idp.server.basic.jose.JsonWebSignatureFactory;
import org.idp.server.basic.jose.JwkInvalidException;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ConfigurationInvalidException;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.grant.AuthorizationGrant;
import org.idp.server.type.extension.CreatedAt;
import org.idp.server.type.extension.ExpiredAt;
import org.idp.server.type.oauth.AccessTokenValue;
import org.idp.server.type.oauth.ExpiresIn;
import org.idp.server.type.oauth.TokenType;

public interface AccessTokenCreatable {

  default AccessToken createAccessToken(
      AuthorizationGrant authorizationGrant,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    try {
      LocalDateTime localDateTime = SystemDateTime.now();
      CreatedAt createdAt = new CreatedAt(localDateTime);
      long accessTokenDuration = serverConfiguration.accessTokenDuration();
      ExpiresIn expiresIn = new ExpiresIn(accessTokenDuration);
      ExpiredAt expiredAt = new ExpiredAt(localDateTime.plusSeconds(accessTokenDuration));
      AccessTokenPayloadBuilder builder = new AccessTokenPayloadBuilder();
      builder.add(serverConfiguration.tokenIssuer());
      if (authorizationGrant.hasUser()) {
        builder.add(authorizationGrant.subject());
      }
      builder.add(authorizationGrant.clientId());
      builder.add(authorizationGrant.scopes());
      builder.add(authorizationGrant.customProperties());
      builder.add(createdAt);
      builder.add(expiredAt);
      Map<String, Object> accessTokenPayload = builder.build();
      JsonWebSignatureFactory jsonWebSignatureFactory = new JsonWebSignatureFactory();
      JsonWebSignature jsonWebSignature =
          jsonWebSignatureFactory.createWithAsymmetricKey(
              accessTokenPayload,
              Map.of(),
              serverConfiguration.jwks(),
              serverConfiguration.tokenSignedKeyId());
      AccessTokenValue accessTokenValue = new AccessTokenValue(jsonWebSignature.serialize());
      return new AccessToken(
          serverConfiguration.tokenIssuer(),
          TokenType.Bearer,
          accessTokenValue,
          authorizationGrant,
          createdAt,
          expiresIn,
          expiredAt);
    } catch (JwkInvalidException jwkInvalidException) {
      throw new ConfigurationInvalidException(jwkInvalidException);
    }
  }
}
