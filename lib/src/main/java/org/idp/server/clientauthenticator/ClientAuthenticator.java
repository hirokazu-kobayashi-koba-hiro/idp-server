package org.idp.server.clientauthenticator;

public interface ClientAuthenticator {

  // FIXME consider backchannel request, token revocation etc
  void authenticate(BackchannelRequestContext context);
}
