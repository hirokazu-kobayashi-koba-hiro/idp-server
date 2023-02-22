package org.idp.server.basic.jose;

import com.nimbusds.jose.jwk.JWK;
import java.util.Objects;

/** JsonWebKey */
public class JsonWebKey {
  JWK value;

  public JsonWebKey() {}

  public JsonWebKey(JWK value) {
    this.value = value;
  }

  public JWK value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value);
  }
}
