package org.idp.server.token;

public interface OAuthTokenCreationService {

  OAuthToken create(TokenRequestContext tokenRequestContext);
}
