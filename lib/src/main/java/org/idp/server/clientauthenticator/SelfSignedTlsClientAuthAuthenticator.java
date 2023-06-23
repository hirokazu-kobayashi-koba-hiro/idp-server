package org.idp.server.clientauthenticator;

import org.idp.server.oauth.clientcredentials.ClientAuthenticationPublicKey;
import org.idp.server.oauth.clientcredentials.ClientCredentials;
import org.idp.server.oauth.mtls.ClientCertification;
import org.idp.server.type.oauth.ClientAuthenticationType;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.ClientSecret;

class SelfSignedTlsClientAuthAuthenticator implements ClientAuthenticator {

  @Override
  public ClientCredentials authenticate(BackchannelRequestContext context) {
    ClientId clientId = context.clientConfiguration().clientId();
    ClientSecret clientSecret = new ClientSecret();
    ClientCertification clientCertification = new ClientCertification();
    return new ClientCredentials(
        clientId,
        ClientAuthenticationType.self_signed_tls_client_auth,
        clientSecret,
        new ClientAuthenticationPublicKey(),
        clientCertification);
  }
}
