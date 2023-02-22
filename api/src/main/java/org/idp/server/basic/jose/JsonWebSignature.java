package org.idp.server.basic.jose;

import com.nimbusds.jwt.SignedJWT;
import java.util.Objects;

/** JsonWebSignature */
public class JsonWebSignature {
  SignedJWT value;

  public JsonWebSignature() {}

  public JsonWebSignature(SignedJWT value) {
    this.value = value;
  }

  public SignedJWT value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value);
  }
}
