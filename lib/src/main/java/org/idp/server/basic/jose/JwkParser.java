package org.idp.server.basic.jose;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import java.text.ParseException;
import java.util.List;
import java.util.Map;

/** JwkParser */
public class JwkParser {

  public static JsonWebKey parse(String value) throws JwkInvalidException {
    try {
      JWK jwk = JWK.parse(value);
      return new JsonWebKey(jwk);
    } catch (ParseException e) {
      throw new JwkInvalidException(e.getMessage(), e);
    }
  }

  public static JsonWebKeys parseKeys(String value) throws JwkInvalidException {
    try {
      JWKSet jwkSet = JWKSet.parse(value);
      List<JWK> keys = jwkSet.getKeys();
      List<JsonWebKey> jsonWebKeys = keys.stream().map(JsonWebKey::new).toList();
      return new JsonWebKeys(jsonWebKeys);
    } catch (ParseException e) {
      throw new JwkInvalidException(e.getMessage(), e);
    }
  }

  // FIXME consider where should is implementing
  public static Map<String, Object> parsePublicKeys(String value) throws JwkInvalidException {
    try {
      JWKSet jwkSet = JWKSet.parse(value);
      JWKSet publicJWKSet = jwkSet.toPublicJWKSet();
      return publicJWKSet.toJSONObject();
    } catch (ParseException e) {
      throw new JwkInvalidException(e.getMessage(), e);
    }
  }
}
