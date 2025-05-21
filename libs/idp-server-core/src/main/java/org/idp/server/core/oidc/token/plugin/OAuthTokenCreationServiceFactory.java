package org.idp.server.core.oidc.token.plugin;

import org.idp.server.core.oidc.token.service.OAuthTokenCreationService;
import org.idp.server.platform.dependency.ApplicationComponentContainer;

public interface OAuthTokenCreationServiceFactory {

  OAuthTokenCreationService create(ApplicationComponentContainer container);
}
