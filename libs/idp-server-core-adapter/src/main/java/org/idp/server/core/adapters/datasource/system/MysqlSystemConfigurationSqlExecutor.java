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

package org.idp.server.core.adapters.datasource.system;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.system.SystemConfiguration;

public class MysqlSystemConfigurationSqlExecutor implements SystemConfigurationSqlExecutor {

  private static final String SYSTEM_ID = "system";
  private final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Override
  public Map<String, String> selectOne() {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        SELECT id, configuration
        FROM system_configuration
        WHERE id = ?
        """;

    List<Object> params = new ArrayList<>();
    params.add(SYSTEM_ID);

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public void upsert(SystemConfiguration configuration) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        INSERT INTO system_configuration (id, configuration, updated_at)
        VALUES (?, ?, NOW(6))
        ON DUPLICATE KEY UPDATE
            configuration = VALUES(configuration),
            updated_at = NOW(6)
        """;

    List<Object> params = new ArrayList<>();
    params.add(SYSTEM_ID);
    params.add(jsonConverter.write(configuration.toMap()));

    sqlExecutor.execute(sqlTemplate, params);
  }
}
