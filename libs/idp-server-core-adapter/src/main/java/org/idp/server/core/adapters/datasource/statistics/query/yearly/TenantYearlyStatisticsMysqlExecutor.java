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

package org.idp.server.core.adapters.datasource.statistics.query.yearly;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class TenantYearlyStatisticsMysqlExecutor implements TenantYearlyStatisticsSqlExecutor {

  @Override
  public Map<String, String> selectByYear(TenantIdentifier tenantId, LocalDate statYear) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sql =
        """
            SELECT id, tenant_id, stat_year, yearly_summary, created_at, updated_at
            FROM statistics_yearly
            WHERE tenant_id = UUID_TO_BIN(?) AND stat_year = ?
            """;

    List<Object> params = new ArrayList<>();
    params.add(tenantId.value());
    params.add(statYear);

    return sqlExecutor.selectOne(sql, params);
  }

  @Override
  public Map<String, String> selectExists(TenantIdentifier tenantId, LocalDate statYear) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sql =
        """
            SELECT COUNT(*) as count
            FROM statistics_yearly
            WHERE tenant_id = UUID_TO_BIN(?) AND stat_year = ?
            """;

    List<Object> params = new ArrayList<>();
    params.add(tenantId.value());
    params.add(statYear);

    return sqlExecutor.selectOne(sql, params);
  }
}
