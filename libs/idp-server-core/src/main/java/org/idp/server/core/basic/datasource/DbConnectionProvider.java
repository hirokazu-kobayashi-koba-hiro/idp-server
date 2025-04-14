package org.idp.server.core.basic.datasource;

import java.sql.Connection;

public interface DbConnectionProvider {
  Connection getConnection(DatabaseType databaseType);
}
