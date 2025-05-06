package org.idp.server.core.security.hook.ssf;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.basic.jose.JsonWebKeyInvalidException;
import org.idp.server.basic.jose.JsonWebSignature;
import org.idp.server.basic.jose.JsonWebSignatureFactory;

public class SecurityEventTokenCreator {

  SecurityEventTokenEntity securityEventTokenEntity;
  String privateKey;

  public SecurityEventTokenCreator(SecurityEventTokenEntity securityEventTokenEntity, String privateKey) {
    this.securityEventTokenEntity = securityEventTokenEntity;
    this.privateKey = privateKey;
  }

  public SecurityEventToken create() {

    try {
      JsonWebSignatureFactory jsonWebSignatureFactory = new JsonWebSignatureFactory();

      Map<String, Object> claims = new HashMap<>();
      claims.put("iss", securityEventTokenEntity.issuerValue());
      claims.put("jti", UUID.randomUUID().toString());
      claims.put("iat", SystemDateTime.now().toEpochSecond(SystemDateTime.zoneOffset));
      claims.put("aud", securityEventTokenEntity.clientIdValue());
      claims.put("events", securityEventTokenEntity.eventAsMap());

      Map<String, Object> headers = new HashMap<>();

      JsonWebSignature jsonWebSignature = jsonWebSignatureFactory.createWithAsymmetricKey(claims, headers, privateKey);
      String jws = jsonWebSignature.serialize();

      return new SecurityEventToken(jws);
    } catch (JsonWebKeyInvalidException e) {
      throw new SecurityEventTokenCreationFailedException("security event token creation is failed.", e);
    }
  }
}
