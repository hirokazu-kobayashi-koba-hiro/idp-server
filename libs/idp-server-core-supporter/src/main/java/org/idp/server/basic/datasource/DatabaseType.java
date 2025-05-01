package org.idp.server.basic.datasource;

import org.idp.server.basic.exception.UnSupportedException;

public enum DatabaseType {
  POSTGRESQL,
  SPANNER,
  MYSQL;

  public static DatabaseType of(String databaseType) {

    for (DatabaseType dbType : DatabaseType.values()) {
      if (dbType.name().equalsIgnoreCase(databaseType)) {
        return dbType;
      }
    }

    throw new UnSupportedException("Unsupported database type: " + databaseType);
  }
}
