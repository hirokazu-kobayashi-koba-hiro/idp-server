/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.clientcredentials;

import java.util.Objects;
import org.idp.server.basic.jose.JsonWebKey;
import org.idp.server.basic.jose.JsonWebKeyType;

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
