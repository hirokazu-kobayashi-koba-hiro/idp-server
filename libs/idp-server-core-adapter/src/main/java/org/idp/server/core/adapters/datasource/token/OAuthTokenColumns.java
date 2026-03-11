/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.adapters.datasource.token;

import java.util.List;

/**
 * Single source of truth for the oauth_token columns that make up the cache row shape.
 *
 * <p>The query-side SELECT is generated from {@link #SELECT_COLUMNS}, and the command-side INSERT
 * builds its cache row map with the same column names. The parity is guarded by {@code
 * OAuthTokenInsertRowParityTest} so that adding a column to one side without the other fails fast
 * instead of silently breaking only the cache-hit path.
 */
public final class OAuthTokenColumns {

  private OAuthTokenColumns() {}

  public static final List<String> SELECT_COLUMNS =
      List.of(
          "id",
          "tenant_id",
          "token_issuer",
          "token_type",
          "encrypted_access_token",
          "hashed_access_token",
          "access_token_custom_claims",
          "user_id",
          "user_payload",
          "authentication",
          "client_id",
          "client_payload",
          "grant_type",
          "scopes",
          "id_token_claims",
          "userinfo_claims",
          "custom_properties",
          "authorization_details",
          "expires_in",
          "access_token_expires_at",
          "access_token_created_at",
          "encrypted_refresh_token",
          "hashed_refresh_token",
          "refresh_token_expires_at",
          "refresh_token_created_at",
          "id_token",
          "client_certification_thumbprint",
          "jwk_thumbprint",
          "c_nonce",
          "c_nonce_expires_in");

  public static String selectClause() {
    return "SELECT " + String.join(", ", SELECT_COLUMNS) + "\n";
  }
}
