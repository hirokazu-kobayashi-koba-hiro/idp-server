package org.idp.server.core.oidc.federation.plugin;

import java.util.ServiceLoader;
import org.idp.server.platform.log.LoggerWrapper;

public class FederationDependencyContainerLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(FederationDependencyContainerLoader.class);

  public static FederationDependencyContainer load() {
    FederationDependencyContainer container = new FederationDependencyContainer();
    ServiceLoader<FederationDependencyProvider> loader =
        ServiceLoader.load(FederationDependencyProvider.class);

    for (FederationDependencyProvider<?> provider : loader) {
      container.register(provider.type(), provider.provide());
      log.info("Dynamic Registered federation dependency provider " + provider.type());
    }

    return container;
  }
}
