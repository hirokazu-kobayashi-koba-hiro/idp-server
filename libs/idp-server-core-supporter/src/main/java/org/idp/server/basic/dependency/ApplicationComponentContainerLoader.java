package org.idp.server.basic.dependency;

import java.util.ServiceLoader;
import org.idp.server.basic.log.LoggerWrapper;

public class ApplicationComponentContainerLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(ApplicationComponentContainerLoader.class);

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
