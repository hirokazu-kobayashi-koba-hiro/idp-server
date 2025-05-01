package org.idp.server.core.oidc.clientauthenticator;

import org.idp.server.core.oidc.clientcredentials.ClientCredentials;

public interface ClientAuthenticator {
  ClientCredentials authenticate(BackchannelRequestContext context);
}
