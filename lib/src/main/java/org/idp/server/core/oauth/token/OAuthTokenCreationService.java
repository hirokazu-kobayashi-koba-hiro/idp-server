package org.idp.server.core.oauth.token;

import org.idp.server.core.oauth.TokenRequestContext;

public interface OAuthTokenCreationService {

  OAuthToken create(TokenRequestContext tokenRequestContext);
}
