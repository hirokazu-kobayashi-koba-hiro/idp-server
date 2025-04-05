package org.idp.server.core.oauth;

public interface OAuthRequestDelegate {

  OAuthSession findOrInitialize(OAuthSessionKey oAuthSessionKey);

  OAuthSession find(OAuthSessionKey oAuthSessionKey);

  void registerSession(OAuthSession oAuthSession);

  void updateSession(OAuthSession oAuthSession);

  void deleteSession(OAuthSessionKey oAuthSessionKey);
}
