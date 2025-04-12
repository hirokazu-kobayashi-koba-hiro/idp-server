package org.idp.server.core.basic.datasource;

import java.util.ServiceLoader;
import java.util.logging.Logger;

public class DataSourceDependencyContainerLoader {

  private static final Logger log =
      Logger.getLogger(DataSourceDependencyContainerLoader.class.getName());

  public static DataSourceDependencyContainer load() {
    DataSourceDependencyContainer container = new DataSourceDependencyContainer();
    ServiceLoader<DataSourceDependencyProvider> loader =
        ServiceLoader.load(DataSourceDependencyProvider.class);

    for (DataSourceDependencyProvider<?> provider : loader) {
      container.register(provider.type(), provider.provide());
      log.info("Dynamic Registered DataSource dependency provider " + provider.type());
    }

    return container;
  }
}
