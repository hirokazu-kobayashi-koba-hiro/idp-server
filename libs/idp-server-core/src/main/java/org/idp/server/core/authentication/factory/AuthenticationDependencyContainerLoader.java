package org.idp.server.core.authentication.factory;

import java.util.ServiceLoader;
import org.idp.server.basic.log.LoggerWrapper;

public class AuthenticationDependencyContainerLoader {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(AuthenticationDependencyContainerLoader.class);

  public static AuthenticationDependencyContainer load() {
    AuthenticationDependencyContainer container = new AuthenticationDependencyContainer();
    ServiceLoader<AuthenticationDependencyProvider> loader = ServiceLoader.load(AuthenticationDependencyProvider.class);

    for (AuthenticationDependencyProvider<?> provider : loader) {
      container.register(provider.type(), provider.provide());
      log.info("Dynamic Registered MFA dependency provider " + provider.type());
    }

    return container;
  }
}
