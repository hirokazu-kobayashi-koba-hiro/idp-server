package org.idp.server.basic.dependency;

import java.util.ServiceLoader;
import java.util.logging.Logger;

public class ApplicationComponentContainerLoader {

  private static final Logger log =
      Logger.getLogger(ApplicationComponentContainerLoader.class.getName());

  public static ApplicationComponentContainer load(
      ApplicationComponentDependencyContainer dependencyContainer) {
    ApplicationComponentContainer container = new ApplicationComponentContainer();
    ServiceLoader<ApplicationComponentProvider> loader =
        ServiceLoader.load(ApplicationComponentProvider.class);

    for (ApplicationComponentProvider<?> provider : loader) {
      container.register(provider.type(), provider.provide(dependencyContainer));
      log.info("Dynamic Registered application component " + provider.type());
    }

    return container;
  }
}
