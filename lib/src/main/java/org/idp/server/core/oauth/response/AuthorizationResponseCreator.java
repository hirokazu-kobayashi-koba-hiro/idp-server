package org.idp.server.core.oauth.response;

import org.idp.server.core.oauth.OAuthAuthorizeContext;

public interface AuthorizationResponseCreator {

  AuthorizationResponse create(OAuthAuthorizeContext context);
}
