package org.idp.server.basic.jose;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import java.text.ParseException;
import java.util.List;

/** JwkParser */
public class JwkParser {

  public static JsonWebKey parse(String value) {
    try {
      JWK jwk = JWK.parse(value);
      return new JsonWebKey(jwk);
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
  }

  public static JsonWebKeys parseKeys(String value) {
    try {
      JWKSet jwkSet = JWKSet.parse(value);
      List<JWK> keys = jwkSet.getKeys();
      List<JsonWebKey> jsonWebKeys = keys.stream().map(JsonWebKey::new).toList();
      return new JsonWebKeys(jsonWebKeys);
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
  }
}
