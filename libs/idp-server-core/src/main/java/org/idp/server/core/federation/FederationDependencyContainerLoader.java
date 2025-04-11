package org.idp.server.core.federation;

import org.idp.server.core.authentication.AuthenticationDependencyContainer;
import org.idp.server.core.authentication.AuthenticationDependencyProvider;

import java.util.ServiceLoader;
import java.util.logging.Logger;

public class FederationDependencyContainerLoader {

  private static final Logger log =
      Logger.getLogger(FederationDependencyContainerLoader.class.getName());

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
