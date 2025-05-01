package org.idp.server.core.oidc.response;

import org.idp.server.core.oidc.OAuthAuthorizeContext;

public interface AuthorizationResponseCreator {

  AuthorizationResponse create(OAuthAuthorizeContext context);
}
