package org.idp.server.clientauthenticator;

import org.idp.server.oauth.clientcredentials.ClientCredentials;

public interface ClientAuthenticator {
  ClientCredentials authenticate(BackchannelRequestContext context);
}
