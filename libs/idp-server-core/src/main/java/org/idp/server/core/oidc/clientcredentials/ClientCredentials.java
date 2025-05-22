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
import org.idp.server.basic.type.oauth.ClientAuthenticationType;
import org.idp.server.basic.type.oauth.ClientSecret;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.core.oidc.mtls.ClientCertification;

public class ClientCredentials {
  RequestedClientId requestedClientId;
  ClientAuthenticationType clientAuthenticationType;
  ClientSecret clientSecret;
  ClientAuthenticationPublicKey clientAuthenticationPublicKey;
  ClientAssertionJwt clientAssertionJwt;
  ClientCertification clientCertification;

  public ClientCredentials() {}

  public ClientCredentials(
      RequestedClientId requestedClientId,
      ClientAuthenticationType clientAuthenticationType,
      ClientSecret clientSecret,
      ClientAuthenticationPublicKey clientAuthenticationPublicKey,
      ClientAssertionJwt clientAssertionJwt,
      ClientCertification clientCertification) {
    this.requestedClientId = requestedClientId;
    this.clientAuthenticationType = clientAuthenticationType;
    this.clientSecret = clientSecret;
    this.clientAuthenticationPublicKey = clientAuthenticationPublicKey;
    this.clientAssertionJwt = clientAssertionJwt;
    this.clientCertification = clientCertification;
  }

  public RequestedClientId clientId() {
    return requestedClientId;
  }

  public ClientAuthenticationType clientAuthenticationType() {
    return clientAuthenticationType;
  }

  public ClientSecret clientSecret() {
    return clientSecret;
  }

  public ClientAuthenticationPublicKey clientAuthenticationPublicKey() {
    return clientAuthenticationPublicKey;
  }

  public ClientAssertionJwt clientAssertionJwt() {
    return clientAssertionJwt;
  }

  public ClientCertification clientCertification() {
    return clientCertification;
  }

  public boolean isTlsClientAuthOrSelfSignedTlsClientAuth() {
    if (Objects.isNull(clientAuthenticationType)) {
      return false;
    }
    return clientAuthenticationType == ClientAuthenticationType.tls_client_auth
        || clientAuthenticationType == ClientAuthenticationType.self_signed_tls_client_auth;
  }
}
