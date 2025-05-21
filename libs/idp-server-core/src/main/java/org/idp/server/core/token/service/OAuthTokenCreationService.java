package org.idp.server.core.token.service;

import org.idp.server.basic.type.oauth.GrantType;
import org.idp.server.core.oidc.clientcredentials.ClientCredentials;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.token.TokenRequestContext;

public interface OAuthTokenCreationService {

  GrantType grantType();

  OAuthToken create(TokenRequestContext tokenRequestContext, ClientCredentials clientCredentials);
}
