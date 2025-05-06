package org.idp.server.basic.dependency.protocol;

import java.util.ServiceLoader;
import org.idp.server.basic.dependency.ApplicationComponentContainer;
import org.idp.server.basic.log.LoggerWrapper;

public class ProtocolContainerLoader {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(ProtocolContainerLoader.class);

  public static ProtocolContainer load(ApplicationComponentContainer applicationComponentContainer) {
    ProtocolContainer container = new ProtocolContainer();
    ServiceLoader<ProtocolProvider> loader = ServiceLoader.load(ProtocolProvider.class);

    for (ProtocolProvider<?> provider : loader) {
      container.register(provider.type(), provider.provide(applicationComponentContainer));
      log.info("Dynamic Registered Protocol provider " + provider.type());
    }

    return container;
  }
}
