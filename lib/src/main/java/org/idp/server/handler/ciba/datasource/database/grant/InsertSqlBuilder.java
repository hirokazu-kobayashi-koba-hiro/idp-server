package org.idp.server.handler.ciba.datasource.database.grant;

class InsertSqlBuilder {
  String sql;
  int columnSize = 13;

  InsertSqlBuilder(String identifier) {
    this.sql =
        """
                INSERT INTO public.ciba_grant
                (backchannel_authentication_request_id, auth_req_id, expired_at, interval, status, user_id, user_payload, authentication, client_id, scopes, claims, custom_properties, authorization_details)
                VALUES ('$1', '$2', '$3', '$4', '$5', '$6', '$7', '$8', '$9', '$10', '$11', '$12', '$13');
                """;
    this.sql = replace(1, identifier);
  }

  InsertSqlBuilder setAuthReqId(String authReqId) {
    this.sql = replace(2, authReqId);
    return this;
  }

  InsertSqlBuilder setExpiredAt(String expiredAt) {
    this.sql = replace(3, expiredAt);
    return this;
  }

  InsertSqlBuilder setInterval(String interval) {
    this.sql = replace(4, interval);
    return this;
  }

  InsertSqlBuilder setStatus(String status) {
    this.sql = replace(5, status);
    return this;
  }

  InsertSqlBuilder setUserId(String userId) {
    this.sql = replace(6, userId);
    return this;
  }

  InsertSqlBuilder setUserPayload(String userPayload) {
    this.sql = replace(7, userPayload);
    return this;
  }

  InsertSqlBuilder setAuthentication(String authentication) {
    this.sql = replace(8, authentication);
    return this;
  }

  InsertSqlBuilder setClientId(String clientId) {
    this.sql = replace(9, clientId);
    return this;
  }

  InsertSqlBuilder setScopes(String scopes) {
    this.sql = replace(10, scopes);
    return this;
  }

  InsertSqlBuilder setClaims(String claims) {
    this.sql = replace(11, claims);
    return this;
  }

  InsertSqlBuilder setCustomProperties(String customProperties) {
    this.sql = replace(12, customProperties);
    return this;
  }

  InsertSqlBuilder setAuthorizationDetails(String authorizationDetails) {
    this.sql = replace(13, authorizationDetails);
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
