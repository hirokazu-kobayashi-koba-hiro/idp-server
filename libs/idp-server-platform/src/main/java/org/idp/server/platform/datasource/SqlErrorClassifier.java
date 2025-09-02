/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.platform.datasource;

import java.sql.SQLException;
import java.util.Optional;

public class SqlErrorClassifier {

  public static SqlError classify(SQLException e) {
    String sqlState = Optional.ofNullable(e.getSQLState()).orElse("");
    int vendorCode = e.getErrorCode();

    // --- PostgreSQL ---
    if ("23505".equals(sqlState)) return SqlError.UNIQUE_VIOLATION;
    if ("23503".equals(sqlState)) return SqlError.FK_VIOLATION;
    if ("23502".equals(sqlState)) return SqlError.NOT_NULL_VIOLATION;
    if ("23514".equals(sqlState)) return SqlError.CHECK_VIOLATION;
    if ("40001".equals(sqlState)) return SqlError.SERIALIZATION_FAILURE;
    if ("40P01".equals(sqlState)) return SqlError.DEADLOCK_DETECTED;

    // --- MySQL/MariaDB ---
    // SQLState is "23000"
    if ("23000".equals(sqlState)) {
      if (vendorCode == 1062) return SqlError.UNIQUE_VIOLATION; // ER_DUP_ENTRY
      if (vendorCode == 1452 || vendorCode == 1451)
        return SqlError.FK_VIOLATION; // add/update child/parent row
      if (vendorCode == 1048) return SqlError.NOT_NULL_VIOLATION; // Column cannot be null
      // CHECKはバージョン依存
    }
    if (vendorCode == 1213) return SqlError.DEADLOCK_DETECTED; // ER_LOCK_DEADLOCK
    if (vendorCode == 1205) return SqlError.SERIALIZATION_FAILURE; // ER_LOCK_WAIT_TIMEOUT

    return SqlError.OTHER;
  }
}
