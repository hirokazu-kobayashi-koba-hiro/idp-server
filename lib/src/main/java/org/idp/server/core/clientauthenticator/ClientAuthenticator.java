package org.idp.server.core.clientauthenticator;

import org.idp.server.core.oauth.clientcredentials.ClientCredentials;

public interface ClientAuthenticator {
  ClientCredentials authenticate(BackchannelRequestContext context);
}
