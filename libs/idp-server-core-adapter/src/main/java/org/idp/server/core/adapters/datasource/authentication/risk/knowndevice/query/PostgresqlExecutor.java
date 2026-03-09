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
package org.idp.server.core.adapters.datasource.authentication.risk.knowndevice.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.authentication.risk.DeviceFingerprint;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements UserKnownDeviceQuerySqlExecutor {

  @Override
  public Map<String, String> selectOne(
      Tenant tenant, User user, DeviceFingerprint deviceFingerprint) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
        SELECT device_fingerprint
        FROM user_known_devices
        WHERE tenant_id = ?::uuid
        AND user_id = ?::uuid
        AND device_fingerprint = ?
        """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());
    params.add(user.subAsUuid());
    params.add(deviceFingerprint.value());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }
}
