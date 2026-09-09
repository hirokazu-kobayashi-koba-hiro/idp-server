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

package org.idp.server.core.adapters.datasource.identity.email;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.email.EmailVerificationChallenge;
import org.idp.server.core.openid.identity.email.EmailVerificationChallengeIdentifier;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class MysqlExecutor implements EmailVerificationChallengeSqlExecutor {

  @Override
  public void insert(Tenant tenant, EmailVerificationChallenge challenge) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            INSERT INTO email_verification_challenge
            (id, tenant_id, user_id, operation, target_email, verification_code, attempts, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    List<Object> params = new ArrayList<>();
    params.add(challenge.identifier().value());
    params.add(tenant.identifier().value());
    params.add(challenge.userIdentifier().value());
    params.add(challenge.operation().value());
    params.add(challenge.targetEmail());
    params.add(challenge.verificationCodeValue());
    params.add(challenge.attempts());
    params.add(challenge.expiresAt());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOneForUpdate(
      Tenant tenant,
      EmailVerificationChallengeIdentifier identifier,
      UserIdentifier userIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    // user_id is part of the predicate on purpose: a challenge owned by someone else must be
    // indistinguishable from one that does not exist, so no caller can forget to check ownership.
    // MySQL has no row level security, so this predicate plus tenant_id is the whole boundary.
    String sqlTemplate =
        """
            SELECT id, user_id, operation, target_email, verification_code, attempts, expires_at
            FROM email_verification_challenge
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
  public void updateAttempts(Tenant tenant, EmailVerificationChallenge challenge) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            UPDATE email_verification_challenge
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
  public void delete(Tenant tenant, EmailVerificationChallengeIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            DELETE FROM email_verification_challenge
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
            DELETE FROM email_verification_challenge
            WHERE tenant_id = ?
            AND user_id = ?
            """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifier().value());
    params.add(userIdentifier.value());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
