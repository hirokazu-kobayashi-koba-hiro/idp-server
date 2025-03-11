package org.idp.server.core.handler.ciba.datasource.database.grant;

import org.idp.server.core.basic.sql.SqlBaseBuilder;

class InsertSqlBuilder implements SqlBaseBuilder {
  String sql;
  int columnSize = 13;

  InsertSqlBuilder(String identifier) {
    this.sql =
        """
                INSERT INTO public.ciba_grant
                (backchannel_authentication_request_id, auth_req_id, expired_at, interval, status, user_id, user_payload, authentication, client_id, scopes, claims, custom_properties, authorization_details)
                VALUES ('$1', '$2', '$3', '$4', '$5', '$6', '$7', '$8', '$9', '$10', '$11', '$12', '$13');
                """;
    this.sql = replace(sql, 1, identifier);
  }

  InsertSqlBuilder setAuthReqId(String authReqId) {
    this.sql = replace(sql, 2, authReqId);
    return this;
  }

  InsertSqlBuilder setExpiredAt(String expiredAt) {
    this.sql = replace(sql, 3, expiredAt);
    return this;
  }

  InsertSqlBuilder setInterval(String interval) {
    this.sql = replace(sql, 4, interval);
    return this;
  }

  InsertSqlBuilder setStatus(String status) {
    this.sql = replace(sql, 5, status);
    return this;
  }

  InsertSqlBuilder setUserId(String userId) {
    this.sql = replace(sql, 6, userId);
    return this;
  }

  InsertSqlBuilder setUserPayload(String userPayload) {
    this.sql = replace(sql, 7, userPayload);
    return this;
  }

  InsertSqlBuilder setAuthentication(String authentication) {
    this.sql = replace(sql, 8, authentication);
    return this;
  }

  InsertSqlBuilder setClientId(String clientId) {
    this.sql = replace(sql, 9, clientId);
    return this;
  }

  InsertSqlBuilder setScopes(String scopes) {
    this.sql = replace(sql, 10, scopes);
    return this;
  }

  InsertSqlBuilder setClaims(String claims) {
    this.sql = replace(sql, 11, claims);
    return this;
  }

  InsertSqlBuilder setCustomProperties(String customProperties) {
    this.sql = replace(sql, 12, customProperties);
    return this;
  }

  InsertSqlBuilder setAuthorizationDetails(String authorizationDetails) {
    this.sql = replace(sql, 13, authorizationDetails);
    return this;
  }

  String build() {
    return build(sql, columnSize);
  }
}
