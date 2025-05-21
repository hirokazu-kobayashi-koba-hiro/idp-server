package org.idp.server.core.oidc.token.service;

import org.idp.server.basic.type.oauth.GrantType;
import org.idp.server.core.oidc.clientcredentials.ClientCredentials;
import org.idp.server.core.oidc.token.OAuthToken;
import org.idp.server.core.oidc.token.TokenRequestContext;

public interface OAuthTokenCreationService {

  GrantType grantType();

  OAuthToken create(TokenRequestContext tokenRequestContext, ClientCredentials clientCredentials);
}
