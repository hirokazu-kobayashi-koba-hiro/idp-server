package org.idp.server.handler.oauth.datasource.database.code;

import org.idp.server.basic.sql.SqlBaseBuilder;

class InsertSqlBuilder implements SqlBaseBuilder {
  String sql;
  int columnSize = 12;

  InsertSqlBuilder(String identifier) {
    this.sql =
        """
                INSERT INTO public.authorization_code_grant
                (authorization_request_id, authorization_code, user_id, user_payload, authentication, client_id, scopes, claims, custom_properties, authorization_details, expired_at, presentation_definition)
                VALUES ('$1', '$2', '$3', '$4', '$5', '$6', '$7', '$8', '$9', '$10', '$11', '$12');
                """;
    this.sql = replace(sql, 1, identifier);
  }

  InsertSqlBuilder setAuthorizationCode(String authorizationCode) {
    this.sql = replace(sql, 2, authorizationCode);
    return this;
  }

  InsertSqlBuilder setUserId(String userId) {
    this.sql = replace(sql, 3, userId);
    return this;
  }

  InsertSqlBuilder setUserPayload(String userPayload) {
    this.sql = replace(sql, 4, userPayload);
    return this;
  }

  InsertSqlBuilder setAuthentication(String authentication) {
    this.sql = replace(sql, 5, authentication);
    return this;
  }

  InsertSqlBuilder setClientId(String clientId) {
    this.sql = replace(sql, 6, clientId);
    return this;
  }

  InsertSqlBuilder setScopes(String scopes) {
    this.sql = replace(sql, 7, scopes);
    return this;
  }

  InsertSqlBuilder setClaims(String claims) {
    this.sql = replace(sql, 8, claims);
    return this;
  }

  InsertSqlBuilder setCustomProperties(String customProperties) {
    this.sql = replace(sql, 9, customProperties);
    return this;
  }

  InsertSqlBuilder setAuthorizationDetails(String authorizationDetails) {
    this.sql = replace(sql, 10, authorizationDetails);
    return this;
  }

  InsertSqlBuilder setExpiredAt(String expiredAt) {
    this.sql = replace(sql, 11, expiredAt);
    return this;
  }

  InsertSqlBuilder setPresentationDefinition(String presentationDefinition) {
    this.sql = replace(sql, 12, presentationDefinition);
    return this;
  }

  String build() {
    for (int i = 1; i <= columnSize; i++) {
      this.sql = sql.replace("'$" + i + "'", "''");
    }
    System.out.println(sql);
    return sql;
  }
}
