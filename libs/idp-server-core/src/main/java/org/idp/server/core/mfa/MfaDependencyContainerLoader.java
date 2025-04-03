package org.idp.server.core.mfa;

import java.util.ServiceLoader;
import java.util.logging.Logger;

public class MfaDependencyContainerLoader {

  private static Logger log = Logger.getLogger(MfaDependencyContainerLoader.class.getName());

  public static MfaDependencyContainer load() {
    MfaDependencyContainer container = new MfaDependencyContainer();
    ServiceLoader<MfaDependencyProvider> loader = ServiceLoader.load(MfaDependencyProvider.class);

    for (MfaDependencyProvider<?> provider : loader) {
      container.register(provider.type(), provider.provide());
      log.info("Dynamic Registered MFA dependency provider " + provider.type());
    }

    return container;
  }
}
