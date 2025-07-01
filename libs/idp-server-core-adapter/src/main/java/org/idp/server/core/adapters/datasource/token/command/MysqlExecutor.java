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

package org.idp.server.core.adapters.datasource.token.command;

import java.util.List;
import org.idp.server.basic.crypto.AesCipher;
import org.idp.server.basic.crypto.HmacHasher;
import org.idp.server.core.oidc.token.OAuthToken;
import org.idp.server.platform.datasource.SqlExecutor;

public class MysqlExecutor implements OAuthTokenSqlExecutor {

  @Override
  public void insert(OAuthToken oAuthToken, AesCipher aesCipher, HmacHasher hmacHasher) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                             INSERT INTO oauth_token (
                                id,
                                tenant_id,
                                token_issuer,
                                token_type,
                                encrypted_access_token,
                                hashed_access_token,
                                user_id,
                                user_payload,
                                authentication,
                                client_id,
                                client_payload,
                                grant_type,
                                scopes,
                                id_token_claims,
                                userinfo_claims,
                                custom_properties,
                                authorization_details,
                                expires_in,
                                access_token_expires_at,
                                access_token_created_at,
                                encrypted_refresh_token,
                                hashed_refresh_token,
                                refresh_token_expires_at,
                                refresh_token_created_at,
                                id_token,
                                client_certification_thumbprint,
                                c_nonce,
                                c_nonce_expires_in
                                )
                                VALUES (
                                ?::uuid,
                                ?::uuid,
                                ?,
                                ?,
                                ?,
                                ?,
                                ?::uuid,
                                ?::jsonb,
                                ?::jsonb,
                                ?,
                                ?::jsonb,
                                ?,
                                ?,
                                ?,
                                ?,
                                ?::jsonb,
                                ?::jsonb,
                                ?,
                                ?,
                                ?,
                                ?,
                                ?,
                                ?,
                                ?,
                                ?,
                                ?,
                                ?,
                                ?
                                );
                              """;
    List<Object> params = InsertSqlParamsCreator.create(oAuthToken, aesCipher, hmacHasher);
    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void delete(OAuthToken oAuthToken, AesCipher aesCipher, HmacHasher hmacHasher) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate = """
            DELETE FROM oauth_token WHERE id = ?;
            """;
    List<Object> params = List.of(oAuthToken.identifier().value());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
