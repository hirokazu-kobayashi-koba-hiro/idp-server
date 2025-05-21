package org.idp.server.platform.datasource;

import java.sql.Connection;

public interface DbConnectionProvider {
  Connection getConnection(DatabaseType databaseType);
}
