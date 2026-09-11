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

package org.idp.server.core.adapters.datasource.identity.contact;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.contact.ContactVerificationChallenge;
import org.idp.server.core.openid.identity.contact.ContactVerificationChallengeIdentifier;
import org.idp.server.core.openid.identity.contact.ContactVerificationChallengeQueries;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class MysqlExecutor implements ContactVerificationChallengeSqlExecutor {

  @Override
  public void insert(Tenant tenant, ContactVerificationChallenge challenge) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            INSERT INTO contact_verification_challenge
            (id, tenant_id, user_id, operation, target_value, verification_code, attempts, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    List<Object> params = new ArrayList<>();
    params.add(challenge.identifier().value());
    params.add(tenant.identifier().value());
    params.add(challenge.userIdentifier().value());
    params.add(challenge.operation().value());
    params.add(challenge.targetValue());
    params.add(challenge.verificationCodeValue());
    params.add(challenge.attempts());
    params.add(challenge.expiresAt());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOneForUpdate(
      Tenant tenant,
      ContactVerificationChallengeIdentifier identifier,
      UserIdentifier userIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    // user_id is part of the predicate on purpose: a challenge owned by someone else must be
    // indistinguishable from one that does not exist, so no caller can forget to check ownership.
    // MySQL has no row level security, so this predicate plus tenant_id is the whole boundary.
    String sqlTemplate =
        """
            SELECT id, user_id, operation, target_value, verification_code, attempts, expires_at
            FROM contact_verification_challenge
            WHERE id = ?
            AND tenant_id = ?
            AND user_id = ?
            FOR UPDATE
            """;

    List<Object> params = new ArrayList<>();
    params.add(identifier.value());
    params.add(tenant.identifier().value());
    params.add(userIdentifier.value());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public void updateAttempts(Tenant tenant, ContactVerificationChallenge challenge) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            UPDATE contact_verification_challenge
            SET attempts = ?, updated_at = CURRENT_TIMESTAMP(6)
            WHERE id = ?
            AND tenant_id = ?
            """;

    List<Object> params = new ArrayList<>();
    params.add(challenge.attempts());
    params.add(challenge.identifier().value());
    params.add(tenant.identifier().value());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void delete(Tenant tenant, ContactVerificationChallengeIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            DELETE FROM contact_verification_challenge
            WHERE id = ?
            AND tenant_id = ?
            """;

    List<Object> params = new ArrayList<>();
    params.add(identifier.value());
    params.add(tenant.identifier().value());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void deleteAllBy(Tenant tenant, UserIdentifier userIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            DELETE FROM contact_verification_challenge
            WHERE tenant_id = ?
            AND user_id = ?
            """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifier().value());
    params.add(userIdentifier.value());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOne(
      Tenant tenant, ContactVerificationChallengeIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    // No owner predicate: this is the management path, where an operator diagnoses a challenge they
    // do not own. Tenant scoping still applies.
    String sqlTemplate =
        """
            SELECT id, user_id, operation, target_value, verification_code, attempts, expires_at
            FROM contact_verification_challenge
            WHERE id = ?
            AND tenant_id = ?
            """;

    List<Object> params = new ArrayList<>();
    params.add(identifier.value());
    params.add(tenant.identifier().value());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public List<Map<String, String>> selectList(
      Tenant tenant, ContactVerificationChallengeQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT id, user_id, operation, target_value, verification_code, attempts, expires_at
            FROM contact_verification_challenge
            WHERE tenant_id = ?
            """);

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifier().value());
    appendFilters(sql, params, queries, false);
    sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
    params.add(queries.limit());
    params.add(queries.offset());

    return sqlExecutor.selectList(sql.toString(), params);
  }

  @Override
  public Map<String, String> selectCount(
      Tenant tenant, ContactVerificationChallengeQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT COUNT(*) AS count
            FROM contact_verification_challenge
            WHERE tenant_id = ?
            """);

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifier().value());
    appendFilters(sql, params, queries, false);

    return sqlExecutor.selectOne(sql.toString(), params);
  }

  private void appendFilters(
      StringBuilder sql,
      List<Object> params,
      ContactVerificationChallengeQueries queries,
      boolean castUuid) {
    if (queries.hasUserId()) {
      sql.append(castUuid ? " AND user_id = ?" : " AND user_id = ?");
      params.add(queries.userIdentifier().value());
    }
    if (queries.hasOperation()) {
      sql.append(" AND operation = ?");
      params.add(queries.operation().value());
    }
  }
}
