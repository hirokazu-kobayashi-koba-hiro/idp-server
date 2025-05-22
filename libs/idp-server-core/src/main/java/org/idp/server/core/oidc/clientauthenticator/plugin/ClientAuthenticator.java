package org.idp.server.core.oidc.clientauthenticator.plugin;

import org.idp.server.basic.type.oauth.ClientAuthenticationType;
import org.idp.server.core.oidc.clientauthenticator.BackchannelRequestContext;
import org.idp.server.core.oidc.clientcredentials.ClientCredentials;

public interface ClientAuthenticator {
  ClientAuthenticationType type();

  ClientCredentials authenticate(BackchannelRequestContext context);
}
