package org.idp.server.core.identity.verification.delegation;

import org.idp.server.core.identity.verification.configuration.IdentityVerificationOAuthAuthorizationConfiguration;
import org.idp.server.core.type.oauth.AccessTokenEntity;

public interface OAuthAuthorizationResolver {

  AccessTokenEntity resolve(
      IdentityVerificationOAuthAuthorizationConfiguration oAuthAuthorizationConfig);
}
