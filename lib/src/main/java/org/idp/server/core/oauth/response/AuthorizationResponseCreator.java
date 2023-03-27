package org.idp.server.core.oauth.response;

import org.idp.server.core.oauth.OAuthRequestContext;

public interface AuthorizationResponseCreator {

    AuthorizationResponse create(OAuthRequestContext oAuthRequestContext);
}
