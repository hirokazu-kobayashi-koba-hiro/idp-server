package org.idp.server.oauth.token;

import java.time.LocalDateTime;
import java.util.Map;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.basic.jose.JoseInvalidException;
import org.idp.server.basic.jose.JsonWebKeyInvalidException;
import org.idp.server.basic.jose.JsonWebSignature;
import org.idp.server.basic.jose.JsonWebSignatureFactory;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ConfigurationInvalidException;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.clientcredentials.ClientCredentials;
import org.idp.server.oauth.grant.AuthorizationGrant;
import org.idp.server.type.extension.CreatedAt;
import org.idp.server.type.extension.ExpiredAt;
import org.idp.server.type.oauth.AccessTokenValue;
import org.idp.server.type.oauth.ExpiresIn;
import org.idp.server.type.verifiablepresentation.VpToken;

public interface VpTokenCreatable {

  default VpToken createAccessToken(
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

      Map<String, Object> accessTokenPayload = payloadBuilder.build();
      JsonWebSignatureFactory jsonWebSignatureFactory = new JsonWebSignatureFactory();
      JsonWebSignature jsonWebSignature =
          jsonWebSignatureFactory.createWithAsymmetricKey(
              accessTokenPayload,
              Map.of(),
              serverConfiguration.jwks(),
              serverConfiguration.tokenSignedKeyId());
      AccessTokenValue accessTokenValue = new AccessTokenValue(jsonWebSignature.serialize());
      return new VpToken();
    } catch (JoseInvalidException | JsonWebKeyInvalidException exception) {
      throw new ConfigurationInvalidException(exception);
    }
  }
}
