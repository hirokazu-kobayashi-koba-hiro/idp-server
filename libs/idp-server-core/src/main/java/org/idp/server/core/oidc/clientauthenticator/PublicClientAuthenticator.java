package org.idp.server.core.oidc.clientauthenticator;

import org.idp.server.basic.type.oauth.ClientAuthenticationType;
import org.idp.server.core.oidc.clientauthenticator.plugin.ClientAuthenticator;
import org.idp.server.core.oidc.clientcredentials.ClientCredentials;

class PublicClientAuthenticator implements ClientAuthenticator {

  @Override
  public ClientAuthenticationType type() {
    return ClientAuthenticationType.none;
  }

  @Override
  public ClientCredentials authenticate(BackchannelRequestContext context) {
    return new ClientCredentials();
  }
}
