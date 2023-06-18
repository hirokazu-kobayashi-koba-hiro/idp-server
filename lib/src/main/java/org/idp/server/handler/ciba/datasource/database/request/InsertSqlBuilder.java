package org.idp.server.handler.ciba.datasource.database.request;

class InsertSqlBuilder {
  String sql;
  int columnSize = 16;

  InsertSqlBuilder(String identifier) {
    this.sql =
        """
                INSERT INTO public.backchannel_authentication_request
                (id, token_issuer, profile, delivery_mode, scopes, client_id, id_token_hint, login_hint, login_hint_token, acr_values, user_code, client_notification_token, binding_message, requested_expiry, request_object, authorization_details)
                VALUES ('$1', '$2', '$3', '$4', '$5', '$6', '$7', '$8', '$9', '$10', '$11', '$12', '$13', '$14', '$15', '$16');
                """;
    this.sql = replace(1, identifier);
  }

  InsertSqlBuilder setTokenIssuer(String tokenIssuer) {
    this.sql = replace(2, tokenIssuer);
    return this;
  }

  InsertSqlBuilder setProfile(String profile) {
    this.sql = replace(3, profile);
    return this;
  }

  InsertSqlBuilder setDeliveryMode(String deliveryMode) {
    this.sql = replace(4, deliveryMode);
    return this;
  }

  InsertSqlBuilder setScopes(String scopes) {
    this.sql = replace(5, scopes);
    return this;
  }

  InsertSqlBuilder setClientId(String clientId) {
    this.sql = replace(6, clientId);
    return this;
  }

  InsertSqlBuilder setIdTokenHint(String idTokenHint) {
    this.sql = replace(7, idTokenHint);
    return this;
  }

  InsertSqlBuilder setLoginHint(String loginHint) {
    this.sql = replace(8, loginHint);
    return this;
  }

  InsertSqlBuilder setLoginHintToken(String loginHintToken) {
    this.sql = replace(9, loginHintToken);
    return this;
  }

  InsertSqlBuilder setAcrValues(String acrValues) {
    this.sql = replace(10, acrValues);
    return this;
  }

  InsertSqlBuilder setUserCode(String userCode) {
    this.sql = replace(11, userCode);
    return this;
  }

  InsertSqlBuilder setClientNotificationToken(String clientNotificationToken) {
    this.sql = replace(12, clientNotificationToken);
    return this;
  }

  InsertSqlBuilder setBindMessage(String bindMessage) {
    this.sql = replace(13, bindMessage);
    return this;
  }

  InsertSqlBuilder setRequestedExpiry(String requestedExpiry) {
    this.sql = replace(14, requestedExpiry);
    return this;
  }

  InsertSqlBuilder setRequestObject(String requestObject) {
    this.sql = replace(15, requestObject);
    return this;
  }

  InsertSqlBuilder setAuthorizationDetails(String authorizationDetails) {
    this.sql = replace(16, authorizationDetails);
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
