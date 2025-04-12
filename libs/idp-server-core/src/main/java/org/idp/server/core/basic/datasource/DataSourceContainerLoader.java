package org.idp.server.core.basic.datasource;

import java.util.ServiceLoader;
import java.util.logging.Logger;

public class DataSourceContainerLoader {

  private static final Logger log = Logger.getLogger(DataSourceContainerLoader.class.getName());

  public static DataSourceContainer load(DataSourceDependencyContainer dependencyContainer) {
    DataSourceContainer container = new DataSourceContainer();
    ServiceLoader<DataSourceProvider> loader = ServiceLoader.load(DataSourceProvider.class);

    for (DataSourceProvider<?> provider : loader) {
      container.register(provider.type(), provider.provide(dependencyContainer));
      log.info("Dynamic Registered DataSource provider " + provider.type());
    }

    return container;
  }
}
