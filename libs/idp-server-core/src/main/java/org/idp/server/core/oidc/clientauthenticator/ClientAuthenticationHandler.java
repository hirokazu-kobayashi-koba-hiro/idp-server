package org.idp.server.core.oidc.clientauthenticator;

import org.idp.server.core.oidc.clientauthenticator.plugin.ClientAuthenticator;
import org.idp.server.core.oidc.clientcredentials.ClientCredentials;

public class ClientAuthenticationHandler {

  ClientAuthenticators authenticators;

  public ClientAuthenticationHandler() {
    this.authenticators = new ClientAuthenticators();
  }

  public ClientCredentials authenticate(BackchannelRequestContext context) {
    ClientAuthenticator clientAuthenticator =
        authenticators.get(context.clientAuthenticationType());

    ClientAuthenticationVerifier verifier =
        new ClientAuthenticationVerifier(
            context.clientAuthenticationType(), clientAuthenticator, context.serverConfiguration());
    verifier.verify();
    return clientAuthenticator.authenticate(context);
  }
}
