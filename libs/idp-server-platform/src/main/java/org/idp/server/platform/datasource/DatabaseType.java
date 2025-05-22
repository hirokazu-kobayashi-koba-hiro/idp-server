/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.idp.server.platform.datasource;

import org.idp.server.platform.exception.UnSupportedException;

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
