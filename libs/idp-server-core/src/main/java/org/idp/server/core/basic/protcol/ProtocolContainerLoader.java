package org.idp.server.core.basic.protcol;

import java.util.ServiceLoader;
import java.util.logging.Logger;
import org.idp.server.core.basic.datasource.DataSourceContainer;

public class ProtocolContainerLoader {

  private static final Logger log = Logger.getLogger(ProtocolContainerLoader.class.getName());

  public static ProtocolContainer load(DataSourceContainer dataSourceContainer) {
    ProtocolContainer container = new ProtocolContainer();
    ServiceLoader<ProtocolProvider> loader = ServiceLoader.load(ProtocolProvider.class);

    for (ProtocolProvider<?> provider : loader) {
      container.register(provider.type(), provider.provide(dataSourceContainer));
      log.info("Dynamic Registered Protocol provider " + provider.type());
    }

    return container;
  }
}
