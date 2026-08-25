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

package org.idp.server.core.adapters.datasource.oidc.clientattestation.challenge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.oauth.clientattestation.challenge.ClientAttestationChallenge;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements ClientAttestationChallengeSqlExecutor {

  @Override
  public void insert(Tenant tenant, ClientAttestationChallenge challenge) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        INSERT INTO client_attestation_challenge
        (challenge, tenant_id, expires_at)
        VALUES (?, ?::uuid, ?)
        """;

    List<Object> params = new ArrayList<>();
    params.add(challenge.challenge());
    params.add(tenant.identifierUUID());
    params.add(challenge.expiresAt());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOne(Tenant tenant, String challenge) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        SELECT challenge, tenant_id, expires_at, created_at
        FROM client_attestation_challenge
        WHERE tenant_id = ?::uuid
        AND challenge = ?
        """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());
    params.add(challenge);

    return sqlExecutor.selectOne(sqlTemplate, params);
  }
}
