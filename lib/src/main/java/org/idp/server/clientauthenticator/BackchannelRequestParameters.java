package org.idp.server.clientauthenticator;

import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.ClientSecret;

public interface BackchannelRequestParameters {
  ClientId clientId();

  boolean hasClientId();

  ClientSecret clientSecret();

  boolean hasClientSecret();
}
