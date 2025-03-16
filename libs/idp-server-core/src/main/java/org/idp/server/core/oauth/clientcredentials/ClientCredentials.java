package org.idp.server.core.oauth.clientcredentials;

import java.util.Objects;
import org.idp.server.core.oauth.mtls.ClientCertification;
import org.idp.server.core.type.oauth.ClientAuthenticationType;
import org.idp.server.core.type.oauth.ClientId;
import org.idp.server.core.type.oauth.ClientSecret;

public class ClientCredentials {
  ClientId clientId;
  ClientAuthenticationType clientAuthenticationType;
  ClientSecret clientSecret;
  ClientAuthenticationPublicKey clientAuthenticationPublicKey;
  ClientAssertionJwt clientAssertionJwt;
  ClientCertification clientCertification;

  public ClientCredentials() {}

  public ClientCredentials(
      ClientId clientId,
      ClientAuthenticationType clientAuthenticationType,
      ClientSecret clientSecret,
      ClientAuthenticationPublicKey clientAuthenticationPublicKey,
      ClientAssertionJwt clientAssertionJwt,
      ClientCertification clientCertification) {
    this.clientId = clientId;
    this.clientAuthenticationType = clientAuthenticationType;
    this.clientSecret = clientSecret;
    this.clientAuthenticationPublicKey = clientAuthenticationPublicKey;
    this.clientAssertionJwt = clientAssertionJwt;
    this.clientCertification = clientCertification;
  }

  public ClientId clientId() {
    return clientId;
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
