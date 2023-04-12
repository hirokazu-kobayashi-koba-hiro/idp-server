package org.idp.server.authenticator;

import org.idp.server.token.TokenRequestContext;

public interface ClientAuthenticator {

  // FIXME consider backchannel request, token revocation etc
  void authenticate(TokenRequestContext tokenRequestContext);
}
