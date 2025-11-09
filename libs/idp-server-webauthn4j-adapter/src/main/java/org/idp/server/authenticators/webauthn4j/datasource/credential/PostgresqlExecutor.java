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

public class PostgresqlExecutor implements WebAuthn4jCredentialSqlExecutor {

  @Override
  public void register(WebAuthn4jCredential credential) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            INSERT INTO webauthn_credentials (
              id, idp_user_id, username, user_display_name, user_icon,
              rp_id, aaguid, attested_credential_data, signature_algorithm, sign_count,
              attestation_type, rk, cred_protect, transports, created_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, to_timestamp(?::bigint / 1000.0));
            """;
    List<Object> params = new ArrayList<>();
    params.add(credential.id());
    params.add(credential.userId());
    params.add(credential.username());
    params.add(credential.userDisplayName());
    params.add(credential.userIcon());
    params.add(credential.rpId());
    params.add(
        credential.aaguid() != null ? credential.aaguid() : "00000000-0000-0000-0000-000000000000");
    params.add(credential.attestedCredentialData());
    params.add(credential.signatureAlgorithm());
    params.add(credential.signCount());
    params.add(credential.attestationType());
    params.add(credential.rk());
    params.add(credential.credProtect());
    // Convert List<String> to JSON array string
    String transportsJson =
        credential.transports() != null
            ? "["
                + String.join(
                    ",", credential.transports().stream().map(t -> "\"" + t + "\"").toList())
                + "]"
            : "[]";
    params.add(transportsJson);
    params.add(credential.createdAt());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public List<Map<String, Object>> findAll(String userId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            SELECT id, idp_user_id, username, user_display_name, user_icon,
                   rp_id, aaguid, attested_credential_data, signature_algorithm, sign_count,
                   attestation_type, rk, cred_protect, transports,
                   created_at, updated_at, authenticated_at
            FROM webauthn_credentials
            WHERE idp_user_id = ?;
            """;
    List<Object> params = new ArrayList<>();
    params.add(userId);

    return sqlExecutor.selectListWithType(sqlTemplate, params);
  }

  @Override
  public List<Map<String, Object>> findByUsername(String username) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            SELECT id, idp_user_id, username, user_display_name, user_icon,
                   rp_id, aaguid, attested_credential_data, signature_algorithm, sign_count,
                   attestation_type, rk, cred_protect, transports,
                   created_at, updated_at, authenticated_at
            FROM webauthn_credentials
            WHERE username = ?;
            """;
    List<Object> params = new ArrayList<>();
    params.add(username);

    return sqlExecutor.selectListWithType(sqlTemplate, params);
  }

  @Override
  public Map<String, Object> selectOne(String id) {

    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            SELECT id, idp_user_id, username, user_display_name, user_icon,
                   rp_id, aaguid, attested_credential_data, signature_algorithm, sign_count,
                   attestation_type, rk, cred_protect, transports,
                   created_at, updated_at, authenticated_at
            FROM webauthn_credentials
            WHERE id = ?;
            """;
    List<Object> params = new ArrayList<>();
    params.add(id);

    return sqlExecutor.selectOneWithType(sqlTemplate, params);
  }

  @Override
  public void updateSignCount(String credentialId, long signCount) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            UPDATE webauthn_credentials
            SET sign_count = ?,
                authenticated_at = now()
            WHERE id = ?;
            """;
    List<Object> params = new ArrayList<>();
    params.add(signCount);
    params.add(credentialId);

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void delete(String credentialId) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            DELETE FROM webauthn_credentials
            WHERE id = ?;
            """;
    List<Object> params = new ArrayList<>();
    params.add(credentialId);

    sqlExecutor.execute(sqlTemplate, params);
  }
}
