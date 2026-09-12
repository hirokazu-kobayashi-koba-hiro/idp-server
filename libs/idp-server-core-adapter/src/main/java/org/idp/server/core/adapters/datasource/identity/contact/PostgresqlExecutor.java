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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.contact.ContactChannel;
import org.idp.server.core.openid.identity.contact.ContactVerificationChallenge;
import org.idp.server.core.openid.identity.contact.ContactVerificationChallengeIdentifier;
import org.idp.server.core.openid.identity.contact.ContactVerificationChallengeQueries;
import org.idp.server.core.openid.identity.contact.ContactVerificationOperation;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements ContactVerificationChallengeSqlExecutor {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Override
  public void insert(Tenant tenant, ContactVerificationChallenge challenge) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            INSERT INTO contact_verification_challenge
            (id, tenant_id, user_id, operation, target_value, verification_code, external_reference, attempts, expires_at)
            VALUES (?::uuid, ?::uuid, ?::uuid, ?, ?, ?, ?::jsonb, ?, ?)
            """;

    List<Object> params = new ArrayList<>();
    params.add(challenge.identifier().valueAsUuid());
    params.add(tenant.identifier().valueAsUuid());
    params.add(challenge.userIdentifier().valueAsUuid());
    params.add(challenge.operation().value());
    params.add(challenge.targetValue());
    params.add(challenge.verificationCodeValue());
    params.add(externalReferenceJson(challenge));
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
    String sqlTemplate =
        """
            SELECT id, user_id, operation, target_value, verification_code, external_reference, attempts, expires_at
            FROM contact_verification_challenge
            WHERE id = ?::uuid
            AND tenant_id = ?::uuid
            AND user_id = ?::uuid
            FOR UPDATE
            """;

    List<Object> params = new ArrayList<>();
    params.add(identifier.valueAsUuid());
    params.add(tenant.identifier().valueAsUuid());
    params.add(userIdentifier.valueAsUuid());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectSentWithinCooldown(
      Tenant tenant,
      UserIdentifier userIdentifier,
      ContactVerificationOperation operation,
      int cooldownSeconds) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    // The interval is computed by the database so a skewed application clock cannot shorten it.
    String sqlTemplate =
        """
            SELECT COUNT(*) AS count
            FROM contact_verification_challenge
            WHERE tenant_id = ?::uuid
            AND user_id = ?::uuid
            AND operation = ?
            AND created_at > (now() - (? * interval '1 second'))
            """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifier().valueAsUuid());
    params.add(userIdentifier.valueAsUuid());
    params.add(operation.value());
    params.add(cooldownSeconds);

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public void updateAttempts(Tenant tenant, ContactVerificationChallenge challenge) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            UPDATE contact_verification_challenge
            SET attempts = ?, updated_at = now()
            WHERE id = ?::uuid
            AND tenant_id = ?::uuid
            """;

    List<Object> params = new ArrayList<>();
    params.add(challenge.attempts());
    params.add(challenge.identifier().valueAsUuid());
    params.add(tenant.identifier().valueAsUuid());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void delete(Tenant tenant, ContactVerificationChallengeIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            DELETE FROM contact_verification_challenge
            WHERE id = ?::uuid
            AND tenant_id = ?::uuid
            """;

    List<Object> params = new ArrayList<>();
    params.add(identifier.valueAsUuid());
    params.add(tenant.identifier().valueAsUuid());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void deleteAllBy(Tenant tenant, UserIdentifier userIdentifier, ContactChannel channel) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    List<String> operations = ContactVerificationOperation.valuesOf(channel);
    String placeholders = String.join(", ", Collections.nCopies(operations.size(), "?"));
    String sqlTemplate =
        """
            DELETE FROM contact_verification_challenge
            WHERE tenant_id = ?::uuid
            AND user_id = ?::uuid
            AND operation IN (%s)
            """
            .formatted(placeholders);

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifier().valueAsUuid());
    params.add(userIdentifier.valueAsUuid());
    params.addAll(operations);

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
            SELECT id, user_id, operation, target_value, verification_code, external_reference, attempts, expires_at
            FROM contact_verification_challenge
            WHERE id = ?::uuid
            AND tenant_id = ?::uuid
            """;

    List<Object> params = new ArrayList<>();
    params.add(identifier.valueAsUuid());
    params.add(tenant.identifier().valueAsUuid());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public List<Map<String, String>> selectList(
      Tenant tenant, ContactVerificationChallengeQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT id, user_id, operation, target_value, verification_code, external_reference, attempts, expires_at
            FROM contact_verification_challenge
            WHERE tenant_id = ?::uuid
            """);

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifier().valueAsUuid());
    appendFilters(sql, params, queries, true);
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
            WHERE tenant_id = ?::uuid
            """);

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifier().valueAsUuid());
    appendFilters(sql, params, queries, true);

    return sqlExecutor.selectOne(sql.toString(), params);
  }

  private void appendFilters(
      StringBuilder sql,
      List<Object> params,
      ContactVerificationChallengeQueries queries,
      boolean castUuid) {
    if (queries.hasUserId()) {
      sql.append(castUuid ? " AND user_id = ?::uuid" : " AND user_id = ?");
      params.add(
          castUuid ? queries.userIdentifier().valueAsUuid() : queries.userIdentifier().value());
    }
    if (queries.hasOperation()) {
      sql.append(" AND operation = ?");
      params.add(queries.operation().value());
    }
  }

  /**
   * Null rather than {@code "{}"} when idp-server owns the code, so the column reads as "there is
   * no external exchange" rather than "there is one, and it is empty".
   */
  private String externalReferenceJson(ContactVerificationChallenge challenge) {
    if (challenge.externalReference().isEmpty()) {
      return null;
    }
    return jsonConverter.write(challenge.externalReference());
  }
}
