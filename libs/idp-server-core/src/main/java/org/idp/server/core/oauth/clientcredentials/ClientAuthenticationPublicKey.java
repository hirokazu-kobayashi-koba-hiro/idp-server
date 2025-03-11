package org.idp.server.core.oauth.clientcredentials;

import java.util.Objects;
import org.idp.server.core.basic.jose.JsonWebKey;
import org.idp.server.core.basic.jose.JsonWebKeyType;

public class ClientAuthenticationPublicKey {

  JsonWebKey jsonWebKey;

  public ClientAuthenticationPublicKey() {}

  public ClientAuthenticationPublicKey(JsonWebKey jsonWebKey) {
    this.jsonWebKey = jsonWebKey;
  }

  public boolean exists() {
    return Objects.nonNull(jsonWebKey);
  }

  public boolean isRsa() {
    JsonWebKeyType jsonWebKeyType = jsonWebKey.keyType();
    return jsonWebKeyType.isRsa();
  }

  public boolean isEc() {
    JsonWebKeyType jsonWebKeyType = jsonWebKey.keyType();
    return jsonWebKeyType.isEc();
  }

  public int size() {
    return jsonWebKey.size();
  }
}
