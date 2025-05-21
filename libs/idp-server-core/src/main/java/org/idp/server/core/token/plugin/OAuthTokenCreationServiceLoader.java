package org.idp.server.core.token.plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import org.idp.server.basic.type.oauth.GrantType;
import org.idp.server.core.token.service.OAuthTokenCreationService;
import org.idp.server.platform.dependency.ApplicationComponentContainer;
import org.idp.server.platform.log.LoggerWrapper;

public class OAuthTokenCreationServiceLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(OAuthTokenCreationServiceLoader.class);

  public static Map<GrantType, OAuthTokenCreationService> load(
      ApplicationComponentContainer container) {
    Map<GrantType, OAuthTokenCreationService> creationServiceMap = new HashMap<>();

    ServiceLoader<OAuthTokenCreationServiceFactory> serviceLoaders =
        ServiceLoader.load(OAuthTokenCreationServiceFactory.class);

    for (OAuthTokenCreationServiceFactory factory : serviceLoaders) {
      OAuthTokenCreationService oAuthTokenCreationService = factory.create(container);
      log.info(
          "Dynamic Registered  OAuthTokenCreationService "
              + oAuthTokenCreationService.getClass().getSimpleName());
      creationServiceMap.put(oAuthTokenCreationService.grantType(), oAuthTokenCreationService);
    }

    return creationServiceMap;
  }
}
