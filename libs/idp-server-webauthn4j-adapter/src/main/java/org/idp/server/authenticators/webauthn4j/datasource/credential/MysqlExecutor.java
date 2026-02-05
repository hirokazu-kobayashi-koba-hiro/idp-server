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

package org.idp.server.authenticators.webauthn4j.datasource.credential;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.authenticators.webauthn4j.WebAuthn4jCredential;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class MysqlExecutor implements WebAuthn4jCredentialSqlExecutor {

  @Override
  public void register(Tenant tenant, WebAuthn4jCredential credential) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

    String sqlTemplate =
        """
            INSERT INTO webauthn_credentials (
              id, tenant_id, user_id, username, user_display_name,
              rp_id, aaguid, attested_credential_data, signature_algorithm, sign_count,
              rk, backup_eligible, backup_state,
              authenticator, attestation, extensions, device, metadata,
              created_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    CAST(? AS JSON), CAST(? AS JSON), CAST(? AS JSON), CAST(? AS JSON), CAST(? AS JSON),
                    FROM_UNIXTIME(? / 1000));
            """;
    List<Object> params = new ArrayList<>();
    params.add(credential.id());
    params.add(tenant.identifierUUID());
    params.add(credential.userId());
    params.add(credential.username());
    params.add(credential.userDisplayName());
    params.add(credential.rpId());
    params.add(
        credential.aaguid() != null ? credential.aaguid() : "00000000-0000-0000-0000-000000000000");
    params.add(credential.attestedCredentialData());
    params.add(credential.signatureAlgorithm());
    params.add(credential.signCount());
    params.add(credential.rk());
    params.add(credential.backupEligible());
    params.add(credential.backupState());
    // JSON columns
    params.add(jsonConverter.write(credential.authenticator()));
    params.add(jsonConverter.write(credential.attestation()));
    params.add(jsonConverter.write(credential.extensions()));
    params.add(jsonConverter.write(credential.device()));
    params.add(jsonConverter.write(credential.metadata()));
    params.add(credential.createdAt());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public List<Map<String, Object>> findAll(Tenant tenant, String userId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            SELECT id, user_id, username, user_display_name,
                   rp_id, aaguid, attested_credential_data, signature_algorithm, sign_count,
                   rk, backup_eligible, backup_state,
                   authenticator, attestation, extensions, device, metadata,
                   created_at, updated_at, authenticated_at
            FROM webauthn_credentials
            WHERE user_id = ?
              AND tenant_id = ?;
            """;
    List<Object> params = new ArrayList<>();
    params.add(userId);
    params.add(tenant.identifierUUID());

    return sqlExecutor.selectListWithType(sqlTemplate, params);
  }

  @Override
  public List<Map<String, Object>> findByUsername(Tenant tenant, String username) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            SELECT id, user_id, username, user_display_name,
                   rp_id, aaguid, attested_credential_data, signature_algorithm, sign_count,
                   rk, backup_eligible, backup_state,
                   authenticator, attestation, extensions, device, metadata,
                   created_at, updated_at, authenticated_at
            FROM webauthn_credentials
            WHERE username = ?
              AND tenant_id = ?;
            """;
    List<Object> params = new ArrayList<>();
    params.add(username);
    params.add(tenant.identifierUUID());

    return sqlExecutor.selectListWithType(sqlTemplate, params);
  }

  @Override
  public Map<String, Object> selectOne(Tenant tenant, String id) {

    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            SELECT id, user_id, username, user_display_name,
                   rp_id, aaguid, attested_credential_data, signature_algorithm, sign_count,
                   rk, backup_eligible, backup_state,
                   authenticator, attestation, extensions, device, metadata,
                   created_at, updated_at, authenticated_at
            FROM webauthn_credentials
            WHERE id = ?
              AND tenant_id = ?;
            """;
    List<Object> params = new ArrayList<>();
    params.add(id);
    params.add(tenant.identifierUUID());

    return sqlExecutor.selectOneWithType(sqlTemplate, params);
  }

  @Override
  public void updateSignCount(Tenant tenant, String credentialId, long signCount) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            UPDATE webauthn_credentials
            SET sign_count = ?,
                authenticated_at = now()
            WHERE id = ?
              AND tenant_id = ?;
            """;
    List<Object> params = new ArrayList<>();
    params.add(signCount);
    params.add(credentialId);
    params.add(tenant.identifierUUID());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void delete(Tenant tenant, String credentialId) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            DELETE FROM webauthn_credentials
            WHERE id = ?
              AND tenant_id = ?;
            """;
    List<Object> params = new ArrayList<>();
    params.add(credentialId);
    params.add(tenant.identifierUUID());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
