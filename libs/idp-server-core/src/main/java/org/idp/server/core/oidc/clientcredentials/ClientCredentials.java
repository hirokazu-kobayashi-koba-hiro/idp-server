package org.idp.server.core.oidc.clientcredentials;

import java.util.Objects;
import org.idp.server.core.oidc.mtls.ClientCertification;
import org.idp.server.basic.type.oauth.ClientAuthenticationType;
import org.idp.server.basic.type.oauth.ClientSecret;
import org.idp.server.basic.type.oauth.RequestedClientId;

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
