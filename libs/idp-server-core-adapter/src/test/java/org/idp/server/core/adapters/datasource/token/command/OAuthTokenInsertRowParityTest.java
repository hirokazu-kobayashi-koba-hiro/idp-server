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

package org.idp.server.core.adapters.datasource.token.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.idp.server.core.adapters.datasource.token.OAuthTokenColumns;
import org.idp.server.core.openid.authentication.Authentication;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrant;
import org.idp.server.core.openid.grant_management.grant.GrantIdTokenClaims;
import org.idp.server.core.openid.grant_management.grant.GrantUserinfoClaims;
import org.idp.server.core.openid.grant_management.grant.consent.ConsentClaims;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.clientauthenticator.mtls.ClientCertificationThumbprint;
import org.idp.server.core.openid.oauth.configuration.client.ClientAttributes;
import org.idp.server.core.openid.oauth.dpop.JwkThumbprint;
import org.idp.server.core.openid.oauth.rar.AuthorizationDetails;
import org.idp.server.core.openid.oauth.type.extension.CreatedAt;
import org.idp.server.core.openid.oauth.type.extension.CustomProperties;
import org.idp.server.core.openid.oauth.type.extension.ExpiresAt;
import org.idp.server.core.openid.oauth.type.oauth.AccessTokenEntity;
import org.idp.server.core.openid.oauth.type.oauth.ExpiresIn;
import org.idp.server.core.openid.oauth.type.oauth.GrantType;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.core.openid.oauth.type.oauth.TokenIssuer;
import org.idp.server.core.openid.oauth.type.oauth.TokenType;
import org.idp.server.core.openid.token.AccessToken;
import org.idp.server.core.openid.token.AccessTokenCustomClaims;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.OAuthTokenBuilder;
import org.idp.server.core.openid.token.OAuthTokenIdentifier;
import org.idp.server.platform.crypto.AesCipher;
import org.idp.server.platform.crypto.HmacHasher;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.junit.jupiter.api.Test;

/**
 * Guards the contract that the cache row built during INSERT has the same key set as the query-side
 * SELECT ({@link OAuthTokenColumns#SELECT_COLUMNS}), plus the INSERT-only {@code expires_at}.
 * Adding a column to one side without the other fails here instead of silently breaking only the
 * cache-hit path.
 */
class OAuthTokenInsertRowParityTest {

  AesCipher aesCipher = new AesCipher(Base64.getEncoder().encodeToString(new byte[32]));
  HmacHasher hmacHasher = new HmacHasher("test-secret");

  /**
   * The INSERT statement names as many columns as it has placeholders.
   *
   * <p>The row parity test above compares the map of values against the SELECT columns, which says
   * nothing about the statement itself: adding a column name without a {@code ?} leaves both sides
   * of that comparison correct and fails at the database instead. It is loud when it happens —
   * every token issuance errors — but it fails in an environment with a database rather than here.
   */
  @Test
  void insertStatementColumnsMatchPlaceholders() {
    assertColumnsMatchPlaceholders(
        "postgresql",
        org.idp.server.core.adapters.datasource.token.command.PostgresqlExecutor.INSERT_SQL);
    assertColumnsMatchPlaceholders(
        "mysql", org.idp.server.core.adapters.datasource.token.command.MysqlExecutor.INSERT_SQL);
  }

  private void assertColumnsMatchPlaceholders(String dialect, String sql) {
    // Neither list contains a nested parenthesis, so the bounds are the first pair after the table
    // name and the first pair after VALUES.
    int columnsOpen = sql.indexOf('(');
    int columnsClose = sql.indexOf(')', columnsOpen);
    int valuesAt = sql.indexOf("VALUES", columnsClose);
    int valuesOpen = sql.indexOf('(', valuesAt);
    int valuesClose = sql.indexOf(')', valuesOpen);

    List<String> columns = splitNonBlank(sql.substring(columnsOpen + 1, columnsClose));
    List<String> placeholders = splitNonBlank(sql.substring(valuesOpen + 1, valuesClose));

    assertEquals(
        columns.size(),
        placeholders.size(),
        dialect + ": column count and placeholder count differ. columns=" + columns);
  }

  private List<String> splitNonBlank(String value) {
    List<String> result = new ArrayList<>();
    for (String part : value.split(",")) {
      if (!part.trim().isEmpty()) {
        result.add(part.trim());
      }
    }
    return result;
  }

  @Test
  void postgresqlInsertRowMatchesSelectColumns() {
    List<Object> params = new ArrayList<>();
    Map<String, String> row =
        new PostgresqlExecutor().buildParamsAndRow(oAuthToken(), aesCipher, hmacHasher, params);

    assertEquals(expectedColumns(), row.keySet());
    assertEquals(row.size(), params.size());
  }

  @Test
  void mysqlInsertRowMatchesSelectColumns() {
    List<Object> params = new ArrayList<>();
    Map<String, String> row =
        new MysqlExecutor().buildParamsAndRow(oAuthToken(), aesCipher, hmacHasher, params);

    assertEquals(expectedColumns(), row.keySet());
    assertEquals(row.size(), params.size());
  }

  private Set<String> expectedColumns() {
    Set<String> expected = new HashSet<>(OAuthTokenColumns.SELECT_COLUMNS);
    expected.add("expires_at");
    return expected;
  }

  private OAuthToken oAuthToken() {
    TenantIdentifier tenantIdentifier =
        new TenantIdentifier("123e4567-e89b-12d3-a456-426614174000");
    AuthorizationGrant authorizationGrant =
        new AuthorizationGrant(
            tenantIdentifier,
            new User(),
            new Authentication(),
            new RequestedClientId("client-001"),
            new ClientAttributes(),
            GrantType.of("client_credentials"),
            new Scopes("openid"),
            new GrantIdTokenClaims(""),
            new GrantUserinfoClaims(""),
            new CustomProperties(),
            new AuthorizationDetails(),
            new ConsentClaims());

    LocalDateTime now = LocalDateTime.of(2026, 6, 10, 12, 0, 0);
    AccessToken accessToken =
        new AccessToken(
            tenantIdentifier,
            new TokenIssuer("https://idp.example.com"),
            TokenType.Bearer,
            new AccessTokenEntity("access-token-value"),
            authorizationGrant,
            new ClientCertificationThumbprint(""),
            new JwkThumbprint(),
            new AccessTokenCustomClaims(),
            new CreatedAt(now),
            new ExpiresIn(3600),
            new ExpiresAt(now.plusSeconds(3600)));

    return new OAuthTokenBuilder(new OAuthTokenIdentifier("223e4567-e89b-12d3-a456-426614174000"))
        .add(accessToken)
        .build();
  }
}
