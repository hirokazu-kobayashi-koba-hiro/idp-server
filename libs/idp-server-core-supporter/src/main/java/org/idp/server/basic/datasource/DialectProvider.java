package org.idp.server.basic.datasource;

public interface DialectProvider {

  DatabaseType provide(String tenantIdentifier);
}
