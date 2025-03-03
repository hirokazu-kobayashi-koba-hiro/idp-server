package org.idp.server.core.token.service;

import org.idp.server.core.oauth.clientcredentials.ClientCredentials;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.token.TokenRequestContext;

public interface OAuthTokenCreationService {

  OAuthToken create(TokenRequestContext tokenRequestContext, ClientCredentials clientCredentials);
}
