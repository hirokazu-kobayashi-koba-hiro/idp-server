package org.idp.server.oauth.response;

import org.idp.server.oauth.OAuthAuthorizeContext;

public interface AuthorizationResponseCreator {

  AuthorizationResponse create(OAuthAuthorizeContext context);
}
