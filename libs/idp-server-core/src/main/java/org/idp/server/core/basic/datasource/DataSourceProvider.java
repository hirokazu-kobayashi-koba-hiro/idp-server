package org.idp.server.core.basic.datasource;

public interface DataSourceProvider<T> {
  Class<T> type();

  T provide(DataSourceDependencyContainer container);
}
