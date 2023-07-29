package org.idp.server.handler.token.datasource.database;

import org.idp.server.basic.sql.SqlBaseBuilder;

class InsertSqlBuilder implements SqlBaseBuilder {
  String sql;
  int columnSize = 22;

  InsertSqlBuilder(String identifier) {
    this.sql =
        """
                INSERT INTO public.oauth_token (id, token_issuer, token_type, access_token, user_id, user_payload, authentication, client_id, scopes, claims, custom_properties, authorization_details, expires_in, access_token_expired_at, access_token_created_at, refresh_token, refresh_token_expired_at, refresh_token_created_at, id_token, client_certification_thumbprint, c_nonce, c_nonce_expires_in)
                VALUES ('$1', '$2', '$3', '$4', '$5', '$6', '$7', '$8', '$9', '$10', '$11', '$12', '$13', '$14', '$15', '$16', '$17', '$18', '$19', '$20', '$21', '$22');
                """;
    this.sql = replace(sql, 1, identifier);
  }

  InsertSqlBuilder setTokenIssuer(String tokenIssuer) {
    this.sql = replace(sql, 2, tokenIssuer);
    return this;
  }

  InsertSqlBuilder setTokenTYpe(String tokenType) {
    this.sql = replace(sql, 3, tokenType);
    return this;
  }

  InsertSqlBuilder setAccessToken(String accessToken) {
    this.sql = replace(sql, 4, accessToken);
    return this;
  }

  InsertSqlBuilder setUserId(String userId) {
    this.sql = replace(sql, 5, userId);
    return this;
  }

  InsertSqlBuilder setUserPayload(String userPayload) {
    this.sql = replace(sql, 6, userPayload);
    return this;
  }

  InsertSqlBuilder setAuthentication(String authentication) {
    this.sql = replace(sql, 7, authentication);
    return this;
  }

  InsertSqlBuilder setClientId(String clientId) {
    this.sql = replace(sql, 8, clientId);
    return this;
  }

  InsertSqlBuilder setScopes(String scopes) {
    this.sql = replace(sql, 9, scopes);
    return this;
  }

  InsertSqlBuilder setClaims(String claims) {
    this.sql = replace(sql, 10, claims);
    return this;
  }

  InsertSqlBuilder setCustomProperties(String customProperties) {
    this.sql = replace(sql, 11, customProperties);
    return this;
  }

  InsertSqlBuilder setAuthorizationDetails(String authorizationDetails) {
    this.sql = replace(sql, 12, authorizationDetails);
    return this;
  }

  InsertSqlBuilder setExpiredIn(String expiredIn) {
    this.sql = replace(sql, 13, expiredIn);
    return this;
  }

  InsertSqlBuilder setAccessTokenExpiredAt(String accessTokenExpiredAt) {
    this.sql = replace(sql, 14, accessTokenExpiredAt);
    return this;
  }

  InsertSqlBuilder setAccessTokenCreatedAt(String accessTokenCreatedAt) {
    this.sql = replace(sql, 15, accessTokenCreatedAt);
    return this;
  }

  InsertSqlBuilder setRefreshToken(String refreshToken) {
    this.sql = replace(sql, 16, refreshToken);
    return this;
  }

  InsertSqlBuilder setRefreshTokenExpiredAt(String refreshTokenExpiredAt) {
    this.sql = replace(sql, 17, refreshTokenExpiredAt);
    return this;
  }

  InsertSqlBuilder setRefreshTokenCreatedAt(String refreshTokenCreatedAt) {
    this.sql = replace(sql, 18, refreshTokenCreatedAt);
    return this;
  }

  InsertSqlBuilder setIdToken(String idToken) {
    this.sql = replace(sql, 19, idToken);
    return this;
  }

  InsertSqlBuilder setClientCertificationThumbprint(String clientCertificationThumbprint) {
    this.sql = replace(sql, 20, clientCertificationThumbprint);
    return this;
  }

  InsertSqlBuilder setCNonce(String cNonce) {
    this.sql = replace(sql, 21, cNonce);
    return this;
  }

  InsertSqlBuilder setCNonceExpiresIn(String cNonceExpiresIn) {
    this.sql = replace(sql, 22, cNonceExpiresIn);
    return this;
  }

  String build() {
    return build(sql, columnSize);
  }
}
