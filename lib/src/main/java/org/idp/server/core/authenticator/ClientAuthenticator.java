package org.idp.server.core.authenticator;

import org.idp.server.core.token.TokenRequestContext;

public interface ClientAuthenticator {

  // FIXME consider backchannel request, token revocation etc
  void authenticate(TokenRequestContext tokenRequestContext);
}
