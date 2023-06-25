package org.idp.server.oauth.clientcredentials;

import java.util.Objects;
import org.idp.server.basic.jose.JsonWebSignature;
import org.idp.server.basic.jose.JsonWebTokenClaims;

public class ClientAssertionJwt {
  JsonWebSignature jsonWebSignature;

  public ClientAssertionJwt() {}

  public ClientAssertionJwt(JsonWebSignature jsonWebSignature) {
    this.jsonWebSignature = jsonWebSignature;
  }

  public JsonWebTokenClaims claims() {
    return jsonWebSignature.claims();
  }

  public String subject() {
    return claims().getSub();
  }

  public String iss() {
    return claims().getIss();
  }

  public boolean exists() {
    return Objects.nonNull(jsonWebSignature) && jsonWebSignature.exists();
  }
}
