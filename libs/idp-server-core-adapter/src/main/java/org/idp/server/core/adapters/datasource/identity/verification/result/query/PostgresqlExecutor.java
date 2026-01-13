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

package org.idp.server.core.adapters.datasource.identity.verification.result.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.result.IdentityVerificationResultIdentifier;
import org.idp.server.core.extension.identity.verification.result.IdentityVerificationResultQueries;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements IdentityVerificationResultSqlExecutor {

  @Override
  public Map<String, String> selectOne(
      Tenant tenant, User user, IdentityVerificationResultIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + " "
            + """
                 WHERE id = ?::uuid
                 AND tenant_id = ?::uuid
                 AND user_id = ?::uuid;
                """;

    List<Object> params = new ArrayList<>();
    params.add(identifier.valueAsUuid());
    params.add(tenant.identifierUUID());
    params.add(user.subAsUuid());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public List<Map<String, String>> selectList(
      Tenant tenant, User user, IdentityVerificationResultQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    StringBuilder sqlBuilder = new StringBuilder();
    sqlBuilder.append(selectSql);
    sqlBuilder.append(" WHERE user_id = ?::uuid AND tenant_id = ?::uuid");

    List<Object> params = new ArrayList<>();
    params.add(user.subAsUuid());
    params.add(tenant.identifierUUID());

    if (queries.hasVerifiedAtFrom()) {
      sqlBuilder.append(" AND verified_at >= ?");
      params.add(queries.verifiedAtFrom());
    }

    if (queries.hasVerifiedAtTo()) {
      sqlBuilder.append(" AND verified_at <= ?");
      params.add(queries.verifiedAtTo());
    }

    if (queries.hasVerifiedUntilFrom()) {
      sqlBuilder.append(" AND valid_until >= ?");
      params.add(queries.verifiedUntilFrom());
    }

    if (queries.hasVerifiedUntilTo()) {
      sqlBuilder.append(" AND valid_until <= ?");
      params.add(queries.verifiedUntilTo());
    }

    if (queries.hasId()) {
      sqlBuilder.append(" AND id = ?::uuid");
      params.add(queries.idAsUuid());
    }
    if (queries.hasType()) {
      sqlBuilder.append(" AND verification_type = ?");
      params.add(queries.type());
    }

    if (queries.hasApplicationId()) {
      sqlBuilder.append(" AND application_id = ?::uuid");
      params.add(queries.applicationIdAsUuid());
    }

    if (queries.hasSource()) {
      sqlBuilder.append(" AND source = ?");
      params.add(queries.source());
    }

    if (queries.hasVerifiedClaims()) {
      for (Map.Entry<String, String> entry : queries.verifiedClaims().entrySet()) {
        String key = entry.getKey();
        String value = entry.getValue();
        sqlBuilder.append(" AND verified_claims ->> ? = ?");
        params.add(key);
        params.add(value);
      }
    }

    sqlBuilder.append(" ORDER BY created_at DESC");
    sqlBuilder.append(" LIMIT ? OFFSET ?;");
    params.add(queries.limit());
    params.add(queries.offset());

    String sql = sqlBuilder.toString();
    return sqlExecutor.selectList(sql, params);
  }

  @Override
  public Map<String, String> selectCount(
      Tenant tenant, User user, IdentityVerificationResultQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    StringBuilder sqlBuilder = new StringBuilder();
    sqlBuilder.append("SELECT COUNT(*) FROM identity_verification_result");
    sqlBuilder.append(" WHERE user_id = ?::uuid AND tenant_id = ?::uuid");

    List<Object> params = new ArrayList<>();
    params.add(user.subAsUuid());
    params.add(tenant.identifierUUID());

    if (queries.hasVerifiedAtFrom()) {
      sqlBuilder.append(" AND verified_at >= ?");
      params.add(queries.verifiedAtFrom());
    }

    if (queries.hasVerifiedAtTo()) {
      sqlBuilder.append(" AND verified_at <= ?");
      params.add(queries.verifiedAtTo());
    }

    if (queries.hasVerifiedUntilFrom()) {
      sqlBuilder.append(" AND valid_until >= ?");
      params.add(queries.verifiedUntilFrom());
    }

    if (queries.hasVerifiedUntilTo()) {
      sqlBuilder.append(" AND valid_until <= ?");
      params.add(queries.verifiedUntilTo());
    }

    if (queries.hasId()) {
      sqlBuilder.append(" AND id = ?::uuid");
      params.add(queries.idAsUuid());
    }
    if (queries.hasType()) {
      sqlBuilder.append(" AND verification_type = ?");
      params.add(queries.type());
    }

    if (queries.hasApplicationId()) {
      sqlBuilder.append(" AND application_id = ?::uuid");
      params.add(queries.applicationIdAsUuid());
    }

    if (queries.hasSource()) {
      sqlBuilder.append(" AND source = ?");
      params.add(queries.source());
    }

    if (queries.hasVerifiedClaims()) {
      for (Map.Entry<String, String> entry : queries.verifiedClaims().entrySet()) {
        String key = entry.getKey();
        String value = entry.getValue();
        sqlBuilder.append(" AND verified_claims ->> ? = ?");
        params.add(key);
        params.add(value);
      }
    }

    String sql = sqlBuilder.toString();
    return sqlExecutor.selectOne(sql, params);
  }

  String selectSql =
      """
          SELECT id,
                 tenant_id,
                 user_id,
                 application_id,
                 verification_type,
                 verified_claims,
                 verified_at,
                 valid_until,
                 source,
                 source_details,
                 attributes,
                 created_at
                 FROM identity_verification_result
          """;
}
