package org.idp.server.core.identity.trustframework.delegation;

import org.idp.server.core.identity.trustframework.configuration.IdentityVerificationOAuthAuthorizationConfiguration;
import org.idp.server.core.type.oauth.AccessTokenEntity;

public interface OAuthAuthorizationResolver {

  AccessTokenEntity resolve(
      IdentityVerificationOAuthAuthorizationConfiguration oAuthAuthorizationConfig);
}
