package org.idp.server.core.basic.oauth;

import org.idp.server.core.type.oauth.AccessTokenEntity;

public interface OAuthAuthorizationResolver {

  AccessTokenEntity resolve(OAuthAuthorizationConfiguration oAuthAuthorizationConfig);
}
