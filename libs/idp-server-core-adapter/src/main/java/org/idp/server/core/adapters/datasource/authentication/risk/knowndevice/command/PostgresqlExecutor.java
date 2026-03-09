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
package org.idp.server.core.adapters.datasource.authentication.risk.knowndevice.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.openid.authentication.risk.UserKnownDevice;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements UserKnownDeviceCommandSqlExecutor {

  @Override
  public void upsert(Tenant tenant, UserKnownDevice userKnownDevice) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
        INSERT INTO user_known_devices (
        tenant_id,
        user_id,
        device_fingerprint,
        device_os,
        device_browser,
        device_platform,
        ip_address,
        latitude,
        longitude,
        country,
        city,
        login_count,
        first_seen_at,
        last_seen_at
        )
        VALUES (
        ?::uuid,
        ?::uuid,
        ?,
        ?,
        ?,
        ?,
        ?::inet,
        ?,
        ?,
        ?,
        ?,
        1,
        NOW(),
        NOW()
        )
        ON CONFLICT (tenant_id, user_id, device_fingerprint)
        DO UPDATE SET
        ip_address = EXCLUDED.ip_address,
        latitude = EXCLUDED.latitude,
        longitude = EXCLUDED.longitude,
        country = EXCLUDED.country,
        city = EXCLUDED.city,
        login_count = user_known_devices.login_count + 1,
        last_seen_at = NOW();
        """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());
    params.add(userKnownDevice.userId());
    params.add(userKnownDevice.deviceFingerprint().value());
    params.add(userKnownDevice.deviceOs());
    params.add(userKnownDevice.deviceBrowser());
    params.add(userKnownDevice.devicePlatform());
    params.add(userKnownDevice.ipAddress());
    params.add(userKnownDevice.latitude());
    params.add(userKnownDevice.longitude());
    params.add(userKnownDevice.country());
    params.add(userKnownDevice.city());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
