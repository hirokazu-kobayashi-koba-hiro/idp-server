package org.idp.server.handler.oauth.datasource.database.code;

import org.idp.server.basic.json.JsonParser;
import org.idp.server.oauth.grant.AuthorizationCodeGrant;

class InsertSqlCreator {

  static JsonParser jsonParser = JsonParser.createWithSnakeCaseStrategy();

  static String createInsert(AuthorizationCodeGrant authorizationCodeGrant) {
    InsertSqlBuilder builder =
        new InsertSqlBuilder(authorizationCodeGrant.authorizationRequestIdentifier().value())
            .setAuthorizationCode(authorizationCodeGrant.authorizationCode().value())
            .setUserId(authorizationCodeGrant.user().sub())
            .setUserPayload(toJson(authorizationCodeGrant.user()))
            .setAuthentication(toJson(authorizationCodeGrant.authentication()))
            .setClientId(authorizationCodeGrant.clientId().value())
            .setScopes(authorizationCodeGrant.scopes().toStringValues())
            .setExpiredAt(authorizationCodeGrant.expiredAt().toStringValue());
    if (authorizationCodeGrant.authorizationGrant().hasClaim()) {
      builder.setClaims(toJson(authorizationCodeGrant.authorizationGrant().claimsPayload()));
    }

    if (authorizationCodeGrant.authorizationGrant().hasCustomProperties()) {
      builder.setCustomProperties(
          toJson(authorizationCodeGrant.authorizationGrant().customProperties().values()));
    }
    if (authorizationCodeGrant.authorizationGrant().hasAuthorizationDetails()) {
      builder.setAuthorizationDetails(
          toJson(authorizationCodeGrant.authorizationGrant().authorizationDetails().toMapValues()));
    }

    return builder.build();
  }

  private static String toJson(Object value) {
    return jsonParser.write(value);
  }
}
