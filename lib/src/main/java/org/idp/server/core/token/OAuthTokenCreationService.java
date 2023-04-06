package org.idp.server.core.token;

public interface OAuthTokenCreationService {

  OAuthToken create(TokenRequestContext tokenRequestContext);
}
