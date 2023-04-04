package org.idp.server.core.oauth.authenticator;

import org.idp.server.core.oauth.TokenRequestContext;

public interface ClientAuthenticator {

  // FIXME consider backchannel request, token revocation etc
  void authenticate(TokenRequestContext tokenRequestContext);
}
