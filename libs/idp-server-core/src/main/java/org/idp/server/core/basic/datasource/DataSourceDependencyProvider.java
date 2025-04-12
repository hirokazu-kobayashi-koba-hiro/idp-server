package org.idp.server.core.basic.datasource;

public interface DataSourceDependencyProvider<T> {
  Class<T> type();

  T provide();
}
