package org.idp.server.verifiablecredential;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.basic.jose.JoseInvalidException;
import org.idp.server.basic.jose.JsonWebKeyInvalidException;
import org.idp.server.basic.jose.JsonWebSignature;
import org.idp.server.basic.jose.JsonWebSignatureFactory;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.identity.VerifiableCredential;
import org.idp.server.type.extension.CreatedAt;
import org.idp.server.type.extension.ExpiredAt;
import org.idp.server.type.oauth.ExpiresIn;

public class JwtVcJsonVerifiableCredentialCreator implements VerifiableCredentialCreator {

  // FIXME setting value
  public VerifiableCredentialJwt create(
      VerifiableCredential verifiableCredential,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    try {
      LocalDateTime localDateTime = SystemDateTime.now();
      CreatedAt createdAt = new CreatedAt(localDateTime);
      long accessTokenDuration = serverConfiguration.accessTokenDuration();
      ExpiresIn expiresIn = new ExpiresIn(accessTokenDuration);
      ExpiredAt expiredAt = new ExpiredAt(localDateTime.plusSeconds(accessTokenDuration));
      Map<String, Object> claim = new HashMap<>();
      claim.put("jti", UUID.randomUUID().toString());
      claim.put("iss", serverConfiguration.tokenIssuer().value());
      claim.put("iat", createdAt.toEpochSecondWithUtc());
      claim.put("nbf", createdAt.toEpochSecondWithUtc());
      claim.put("exp", expiredAt.toEpochSecondWithUtc());
      claim.put("vc", verifiableCredential.values());
      if (verifiableCredential.hasSubject()) {
        claim.put("sub", verifiableCredential.subject());
      }
      JsonWebSignatureFactory jsonWebSignatureFactory = new JsonWebSignatureFactory();
      JsonWebSignature jsonWebSignature =
          jsonWebSignatureFactory.createWithAsymmetricKey(
              claim,
              Map.of(),
              serverConfiguration.jwks(),
              // FIXME
              serverConfiguration.tokenSignedKeyId());

      return new VerifiableCredentialJwt(jsonWebSignature.serialize());
    } catch (JoseInvalidException | JsonWebKeyInvalidException e) {
      throw new RuntimeException(e);
    }
  }
}
