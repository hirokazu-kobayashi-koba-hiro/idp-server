package org.idp.server.core.token;

import java.util.Map;
import org.idp.server.basic.jose.JsonWebSignature;
import org.idp.server.basic.jose.JsonWebSignatureFactory;
import org.idp.server.basic.jose.JwkInvalidException;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ConfigurationInvalidException;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.type.oauth.AccessToken;

public interface AccessTokenCreatable {

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
