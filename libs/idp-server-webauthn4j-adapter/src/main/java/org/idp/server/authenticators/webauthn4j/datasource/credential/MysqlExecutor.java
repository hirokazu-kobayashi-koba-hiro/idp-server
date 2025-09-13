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

public class MysqlExecutor implements WebAuthn4jCredentialSqlExecutor {

  @Override
  public void register(WebAuthn4jCredential credential) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            INSERT INTO webauthn_credentials (id, idp_user_id, rp_id, attestation_object, sign_count)
            VALUES (?, ?, ?, ?, ?);
            """;
    List<Object> params = new ArrayList<>();
    params.add(credential.id());
    params.add(credential.userId());
    params.add(credential.rpId());
    params.add(credential.attestationObject());
    params.add(credential.signCount());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public List<Map<String, Object>> findAll(String userId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            SELECT id, idp_user_id, rp_id, attestation_object, sign_count
            FROM webauthn_credentials
            WHERE idp_user_id = ?;
            """;
    List<Object> params = new ArrayList<>();
    params.add(userId);

    return sqlExecutor.selectListWithType(sqlTemplate, params);
  }

  @Override
  public void updateSignCount(String credentialId, long signCount) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            UPDATE webauthn_credentials
            SET sign_count = ?
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
