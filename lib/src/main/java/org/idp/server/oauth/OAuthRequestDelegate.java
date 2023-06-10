package org.idp.server.oauth;

public interface OAuthRequestDelegate {

  OAuthSession findSession(OAuthSessionKey oAuthSessionKey);

  void registerSession(OAuthSession oAuthSession);
}
