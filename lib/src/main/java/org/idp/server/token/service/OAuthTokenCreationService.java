package org.idp.server.token.service;

import org.idp.server.oauth.clientcredentials.ClientCredentials;
import org.idp.server.token.OAuthToken;
import org.idp.server.token.TokenRequestContext;

public interface OAuthTokenCreationService {

  OAuthToken create(TokenRequestContext tokenRequestContext, ClientCredentials clientCredentials);
}
