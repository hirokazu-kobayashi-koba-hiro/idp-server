package org.idp.server.oauth.clientcredentials;

import java.util.Objects;
import org.idp.server.basic.jose.JsonWebKey;

public class ClientAuthenticationPublicKey {

  JsonWebKey jsonWebKey;

  public ClientAuthenticationPublicKey() {}

  public ClientAuthenticationPublicKey(JsonWebKey jsonWebKey) {
    this.jsonWebKey = jsonWebKey;
  }

  public boolean exists() {
    return Objects.nonNull(jsonWebKey);
  }
}
