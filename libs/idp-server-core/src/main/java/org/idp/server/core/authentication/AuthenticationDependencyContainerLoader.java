package org.idp.server.core.authentication;

import java.util.ServiceLoader;
import java.util.logging.Logger;

public class AuthenticationDependencyContainerLoader {

  private static final Logger log =
      Logger.getLogger(AuthenticationDependencyContainerLoader.class.getName());

  public static AuthenticationDependencyContainer load() {
    AuthenticationDependencyContainer container = new AuthenticationDependencyContainer();
    ServiceLoader<AuthenticationDependencyProvider> loader =
        ServiceLoader.load(AuthenticationDependencyProvider.class);

    for (AuthenticationDependencyProvider<?> provider : loader) {
      container.register(provider.type(), provider.provide());
      log.info("Dynamic Registered MFA dependency provider " + provider.type());
    }

    return container;
  }
}
