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

package org.idp.server.core.adapters.datasource.identity.device.credential.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.core.openid.identity.device.credential.DeviceCredentialIdentifier;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements DeviceCredentialQuerySqlExecutor {

  static final String TABLE_NAME = "idp_user_authentication_device_credentials";

  String selectSql =
      """
      SELECT
        id,
        tenant_id,
        user_id,
        device_id,
        credential_type,
        type_specific_data,
        created_at,
        expires_at,
        revoked_at
      FROM idp_user_authentication_device_credentials
      """;

  @Override
  public Map<String, String> selectById(Tenant tenant, DeviceCredentialIdentifier credentialId) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + """
            WHERE id = ?
            AND tenant_id = ?::uuid;
            """;

    List<Object> params = new ArrayList<>();
    params.add(credentialId.value());
    params.add(tenant.identifierUUID());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public List<Map<String, String>> selectByDeviceId(
      Tenant tenant, AuthenticationDeviceIdentifier deviceId) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + """
            WHERE device_id = ?::uuid
            AND tenant_id = ?::uuid
            ORDER BY created_at DESC;
            """;

    List<Object> params = new ArrayList<>();
    params.add(deviceId.value());
    params.add(tenant.identifierUUID());

    return sqlExecutor.selectList(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectActiveByDeviceId(
      Tenant tenant, AuthenticationDeviceIdentifier deviceId) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + """
            WHERE device_id = ?::uuid
            AND tenant_id = ?::uuid
            AND revoked_at IS NULL
            AND (expires_at IS NULL OR expires_at > now())
            ORDER BY created_at DESC
            LIMIT 1;
            """;

    List<Object> params = new ArrayList<>();
    params.add(deviceId.value());
    params.add(tenant.identifierUUID());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectActiveByDeviceIdAndAlgorithm(
      Tenant tenant, AuthenticationDeviceIdentifier deviceId, String algorithm) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + """
            WHERE device_id = ?::uuid
            AND tenant_id = ?::uuid
            AND type_specific_data->>'algorithm' = ?
            AND revoked_at IS NULL
            AND (expires_at IS NULL OR expires_at > now())
            ORDER BY created_at DESC
            LIMIT 1;
            """;

    List<Object> params = new ArrayList<>();
    params.add(deviceId.value());
    params.add(tenant.identifierUUID());
    params.add(algorithm);

    return sqlExecutor.selectOne(sqlTemplate, params);
  }
}
