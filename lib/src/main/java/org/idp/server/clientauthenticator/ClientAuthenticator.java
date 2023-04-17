package org.idp.server.clientauthenticator;

public interface ClientAuthenticator {
  void authenticate(BackchannelRequestContext context);
}
