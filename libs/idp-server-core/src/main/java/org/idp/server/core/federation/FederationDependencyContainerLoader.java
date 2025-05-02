package org.idp.server.core.federation;

import java.util.ServiceLoader;
import org.idp.server.basic.log.LoggerWrapper;

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
