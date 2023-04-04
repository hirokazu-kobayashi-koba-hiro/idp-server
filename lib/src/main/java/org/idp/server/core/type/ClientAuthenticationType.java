package org.idp.server.core.type;

public enum ClientAuthenticationType {
  client_secret_basic,
  client_secret_post,
  client_secret_jwt,
  private_key_jwt,
  tls_client_auth,
  self_signed_tls_client_auth,
}
