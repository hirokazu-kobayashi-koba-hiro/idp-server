package org.idp.server.core.basic.sql;

import java.sql.Connection;

public interface ConnectionProvider {
  Connection getConnection(DatabaseType databaseType);
}
