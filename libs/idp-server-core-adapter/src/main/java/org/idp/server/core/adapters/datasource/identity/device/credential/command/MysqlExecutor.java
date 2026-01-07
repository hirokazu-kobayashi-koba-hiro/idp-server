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

package org.idp.server.core.adapters.datasource.identity.device.credential.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.core.openid.identity.device.credential.DeviceCredential;
import org.idp.server.core.openid.identity.device.credential.DeviceCredentialIdentifier;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class MysqlExecutor implements DeviceCredentialCommandSqlExecutor {

  static final String TABLE_NAME = "idp_user_authentication_device_credentials";

  @Override
  public void insert(
      Tenant tenant,
      UserIdentifier userId,
      AuthenticationDeviceIdentifier deviceId,
      DeviceCredential credential) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            INSERT INTO idp_user_authentication_device_credentials (
              id,
              tenant_id,
              user_id,
              device_id,
              credential_type,
              type_specific_data,
              expires_at
            )
            VALUES (
              ?,
              ?,
              ?,
              ?,
              ?,
              ?,
              ?
            );
            """;

    List<Object> params = new ArrayList<>();
    params.add(credential.identifier().value());
    params.add(tenant.identifier().value());
    params.add(userId.value());
    params.add(deviceId.value());
    params.add(credential.type().name());
    params.add(credential.typeSpecificDataAsJson());
    params.add(credential.expiresAtOrNull());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void update(Tenant tenant, DeviceCredential credential) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            UPDATE idp_user_authentication_device_credentials
            SET type_specific_data = ?,
                expires_at = ?,
                updated_at = now()
            WHERE id = ?
            AND tenant_id = ?;
            """;

    List<Object> params = new ArrayList<>();
    params.add(credential.typeSpecificDataAsJson());
    params.add(credential.expiresAtOrNull());
    params.add(credential.identifier().value());
    params.add(tenant.identifier().value());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void revoke(Tenant tenant, DeviceCredentialIdentifier credentialId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            UPDATE idp_user_authentication_device_credentials
            SET revoked_at = now(),
                updated_at = now()
            WHERE id = ?
            AND tenant_id = ?;
            """;

    List<Object> params = new ArrayList<>();
    params.add(credentialId.value());
    params.add(tenant.identifier().value());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void delete(Tenant tenant, DeviceCredentialIdentifier credentialId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            DELETE FROM idp_user_authentication_device_credentials
            WHERE id = ?
            AND tenant_id = ?;
            """;

    List<Object> params = new ArrayList<>();
    params.add(credentialId.value());
    params.add(tenant.identifier().value());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void deleteByDeviceId(Tenant tenant, AuthenticationDeviceIdentifier deviceId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            DELETE FROM idp_user_authentication_device_credentials
            WHERE device_id = ?
            AND tenant_id = ?;
            """;

    List<Object> params = new ArrayList<>();
    params.add(deviceId.value());
    params.add(tenant.identifier().value());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
