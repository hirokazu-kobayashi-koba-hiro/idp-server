package org.idp.server.core.oauth;

public interface OAuthRequestDelegate {

  OAuthSession findSession(OAuthSessionKey oAuthSessionKey);

  void registerSession(OAuthSession oAuthSession);

  void deleteSession(OAuthSessionKey oAuthSessionKey);
}
