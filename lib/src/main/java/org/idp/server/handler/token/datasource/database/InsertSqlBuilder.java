package org.idp.server.handler.token.datasource.database;

class InsertSqlBuilder {
  String sql;
  int columnSize = 19;

  InsertSqlBuilder(String identifier) {
    this.sql =
        """
                INSERT INTO public.oauth_token (id, token_issuer, token_type, access_token, user_id, user_payload, authentication, client_id, scopes, claims, custom_properties, authorization_details, expires_in, access_token_expired_at, access_token_created_at, refresh_token, refresh_token_expired_at, refresh_token_created_at, id_token)
                VALUES ('$1', '$2', '$3', '$4', '$5', '$6', '$7', '$8', '$9', '$10', '$11', '$12', '$13', '$14', '$15', '$16', '$17', '$18', '$19');
                """;
    this.sql = replace(1, identifier);
  }

  InsertSqlBuilder setTokenIssuer(String tokenIssuer) {
    this.sql = replace(2, tokenIssuer);
    return this;
  }

  InsertSqlBuilder setTokenTYpe(String tokenType) {
    this.sql = replace(3, tokenType);
    return this;
  }

  InsertSqlBuilder setAccessToken(String accessToken) {
    this.sql = replace(4, accessToken);
    return this;
  }

  InsertSqlBuilder setUserId(String userId) {
    this.sql = replace(5, userId);
    return this;
  }

  InsertSqlBuilder setUserPayload(String userPayload) {
    this.sql = replace(6, userPayload);
    return this;
  }

  InsertSqlBuilder setAuthentication(String authentication) {
    this.sql = replace(7, authentication);
    return this;
  }

  InsertSqlBuilder setClientId(String clientId) {
    this.sql = replace(8, clientId);
    return this;
  }

  InsertSqlBuilder setScopes(String scopes) {
    this.sql = replace(9, scopes);
    return this;
  }

  InsertSqlBuilder setClaims(String claims) {
    this.sql = replace(10, claims);
    return this;
  }

  InsertSqlBuilder setCustomProperties(String customProperties) {
    this.sql = replace(11, customProperties);
    return this;
  }

  InsertSqlBuilder setAuthorizationDetails(String authorizationDetails) {
    this.sql = replace(12, authorizationDetails);
    return this;
  }

  InsertSqlBuilder setExpiredIn(String expiredIn) {
    this.sql = replace(13, expiredIn);
    return this;
  }

  InsertSqlBuilder setAccessTokenExpiredAt(String accessTokenExpiredAt) {
    this.sql = replace(14, accessTokenExpiredAt);
    return this;
  }

  InsertSqlBuilder setAccessTokenCreatedAt(String accessTokenCreatedAt) {
    this.sql = replace(15, accessTokenCreatedAt);
    return this;
  }

  InsertSqlBuilder setRefreshToken(String refreshToken) {
    this.sql = replace(16, refreshToken);
    return this;
  }

  InsertSqlBuilder setRefreshTokenExpiredAt(String refreshTokenExpiredAt) {
    this.sql = replace(17, refreshTokenExpiredAt);
    return this;
  }

  InsertSqlBuilder setRefreshTokenCreatedAt(String refreshTokenCreatedAt) {
    this.sql = replace(18, refreshTokenCreatedAt);
    return this;
  }

  InsertSqlBuilder setIdToken(String idToken) {
    this.sql = replace(19, idToken);
    return this;
  }

  String build() {
    for (int i = 1; i <= columnSize; i++) {
      this.sql = sql.replace("'$" + i + "'", "''");
    }
    System.out.println(sql);
    return sql;
  }

  private String replace(int index, String value) {
    return sql.replace(String.format("'$%d'", index), String.format("'%s'", value));
  }
}
