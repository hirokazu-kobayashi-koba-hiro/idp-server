/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.type.oauth;

public enum ClientAuthenticationType {
  client_secret_basic,
  client_secret_post,
  client_secret_jwt,
  private_key_jwt,
  tls_client_auth,
  self_signed_tls_client_auth,
  none;

  public boolean isClientSecretBasic() {
    return this == client_secret_basic;
  }

  public boolean isClientSecretPost() {
    return this == client_secret_post;
  }

  public boolean isClientSecretJwt() {
    return this == client_secret_jwt;
  }

  public boolean isPrivateKeyJwt() {
    return this == private_key_jwt;
  }

  public boolean isTlsClientAuth() {
    return this == tls_client_auth;
  }

  public boolean isSelfSignedTlsClientAuth() {
    return this == self_signed_tls_client_auth;
  }

  public boolean isNone() {
    return this == none;
  }
}
