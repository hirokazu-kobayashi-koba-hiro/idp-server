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

package org.idp.server.core.adapters.datasource.oidc.clientinstance.registration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.clientinstance.registration.ClientInstanceRegistrationChallenge;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class MysqlExecutor implements ClientInstanceRegistrationChallengeSqlExecutor {

  @Override
  public void insert(Tenant tenant, ClientInstanceRegistrationChallenge challenge) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        INSERT INTO client_instance_registration_challenge
        (challenge, tenant_id, client_id, device_id, instance_id, expires_at)
        VALUES (?, ?, ?, ?, ?, ?)
        """;

    List<Object> params = new ArrayList<>();
    params.add(challenge.challenge());
    params.add(tenant.identifierValue());
    params.add(challenge.clientId());
    params.add(challenge.deviceId());
    params.add(challenge.instanceId());
    params.add(challenge.expiresAt());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOne(Tenant tenant, String challenge) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        SELECT challenge, tenant_id, client_id, device_id, instance_id,
               expires_at, used_at, created_at
        FROM client_instance_registration_challenge
        WHERE tenant_id = ?
        AND challenge = ?
        """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(challenge);

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public int updateUsedAt(Tenant tenant, String challenge) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    // Conditional update so that two concurrent registrations cannot both consume the challenge.
    String sqlTemplate =
        """
        UPDATE client_instance_registration_challenge
        SET used_at = CURRENT_TIMESTAMP(6)
        WHERE tenant_id = ?
        AND challenge = ?
        AND used_at IS NULL
        """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(challenge);

    return sqlExecutor.executeAndReturnAffectedRows(sqlTemplate, params);
  }
}
