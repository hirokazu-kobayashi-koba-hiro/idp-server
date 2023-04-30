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
import org.idp.server.type.oauth.AccessTokenValue;
import org.idp.server.type.oauth.CreatedAt;
import org.idp.server.type.oauth.ExpiredAt;

public interface AccessTokenCreatable {

  default AccessTokenPayload createAccessTokenPayload(
      AuthorizationGrant authorizationGrant,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    LocalDateTime localDateTime = SystemDateTime.now();
    CreatedAt createdAt = new CreatedAt(localDateTime);
    long accessTokenDuration = serverConfiguration.accessTokenDuration();
    ExpiredAt expiredAt = new ExpiredAt(localDateTime.plusSeconds(accessTokenDuration));
    AccessTokenPayloadBuilder builder = new AccessTokenPayloadBuilder();
    builder.add(serverConfiguration.tokenIssuer());
    builder.add(authorizationGrant.subject());
    builder.add(authorizationGrant.clientId());
    builder.add(authorizationGrant.scopes());
    builder.add(authorizationGrant.customProperties());
    builder.add(createdAt);
    builder.add(expiredAt);
    return builder.build();
  }

  default AccessToken createAccessToken(
      AccessTokenPayload accessTokenPayload,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    try {
      JsonWebSignatureFactory jsonWebSignatureFactory = new JsonWebSignatureFactory();
      JsonWebSignature jsonWebSignature =
          jsonWebSignatureFactory.createWithAsymmetricKey(
              accessTokenPayload.values(),
              Map.of(),
              serverConfiguration.jwks(),
              serverConfiguration.tokenSignedKeyId());
      AccessTokenValue accessTokenValue = new AccessTokenValue(jsonWebSignature.serialize());
      return new AccessToken(
          accessTokenValue,
          accessTokenPayload,
          accessTokenPayload.createdAt(),
          accessTokenPayload.expiredAt());
    } catch (JwkInvalidException jwkInvalidException) {
      throw new ConfigurationInvalidException(jwkInvalidException);
    }
  }
}
