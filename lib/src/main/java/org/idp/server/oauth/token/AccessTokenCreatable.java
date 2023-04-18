package org.idp.server.oauth.token;

import java.time.LocalDateTime;
import java.util.Map;
import org.idp.server.basic.date.UtcDateTime;
import org.idp.server.basic.jose.JsonWebSignature;
import org.idp.server.basic.jose.JsonWebSignatureFactory;
import org.idp.server.basic.jose.JwkInvalidException;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ConfigurationInvalidException;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.grant.AuthorizationGranted;
import org.idp.server.type.oauth.AccessToken;
import org.idp.server.type.oauth.CreatedAt;
import org.idp.server.type.oauth.ExpiredAt;

public interface AccessTokenCreatable {

  default AccessTokenPayload createAccessTokenPayload(
      AuthorizationGranted authorizationGranted,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    LocalDateTime localDateTime = UtcDateTime.now();
    CreatedAt createdAt = new CreatedAt(localDateTime);
    long accessTokenDuration = serverConfiguration.accessTokenDuration();
    ExpiredAt expiredAt = new ExpiredAt(localDateTime.plusSeconds(accessTokenDuration));
    AccessTokenPayloadBuilder builder = new AccessTokenPayloadBuilder();
    builder.add(serverConfiguration.issuer());
    builder.add(authorizationGranted.subject());
    builder.add(authorizationGranted.clientId());
    builder.add(authorizationGranted.scopes());
    builder.add(authorizationGranted.customProperties());
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
      return new AccessToken(jsonWebSignature.serialize());
    } catch (JwkInvalidException jwkInvalidException) {
      throw new ConfigurationInvalidException(jwkInvalidException);
    }
  }
}
