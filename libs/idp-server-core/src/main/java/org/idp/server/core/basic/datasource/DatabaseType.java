package org.idp.server.core.basic.datasource;

import org.idp.server.core.type.exception.UnSupportedException;

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
