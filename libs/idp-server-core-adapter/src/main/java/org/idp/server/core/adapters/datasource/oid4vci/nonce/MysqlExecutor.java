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
package org.idp.server.core.adapters.datasource.oid4vci.nonce;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.extension.oid4vci.nonce.CredentialNonce;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class MysqlExecutor implements CredentialNonceSqlExecutor {

  @Override
  public void insert(Tenant tenant, CredentialNonce nonce) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        INSERT INTO credential_nonce
        (nonce, tenant_id, expires_at)
        VALUES (?, ?, ?)
        """;

    List<Object> params = new ArrayList<>();
    params.add(nonce.value());
    params.add(tenant.identifierValue());
    params.add(nonce.expiresAt());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public int deleteUnexpired(Tenant tenant, String nonce) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        DELETE FROM credential_nonce
        WHERE tenant_id = ?
        AND nonce = ?
        AND expires_at > ?
        """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(nonce);
    params.add(SystemDateTime.now());

    return sqlExecutor.executeAndReturnAffectedRows(sqlTemplate, params);
  }
}
