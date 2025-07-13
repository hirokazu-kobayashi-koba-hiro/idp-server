/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.oidc.type.oauth;

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
