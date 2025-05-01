package org.idp.server.basic.datasource;

import java.sql.Connection;

public interface DbConnectionProvider {
  Connection getConnection(DatabaseType databaseType);
}
