package org.idp.server.handler.ciba.datasource.database.grant;

import org.idp.server.basic.json.JsonParser;
import org.idp.server.ciba.grant.CibaGrant;
import org.idp.server.oauth.grant.AuthorizationGrant;

class SqlCreator {

  static JsonParser jsonParser = JsonParser.createWithSnakeCaseStrategy();

  static String createInsert(CibaGrant cibaGrant) {
    AuthorizationGrant authorizationGrant = cibaGrant.authorizationGrant();
    InsertSqlBuilder builder =
        new InsertSqlBuilder(cibaGrant.backchannelAuthenticationRequestIdentifier().value())
            .setAuthReqId(cibaGrant.authReqId().value())
            .setExpiredAt(cibaGrant.expiredAt().toStringValue())
            .setInterval(cibaGrant.interval().toStringValue())
            .setStatus(cibaGrant.status().name())
            .setUserId(authorizationGrant.user().sub())
            .setUserPayload(toJson(authorizationGrant.user()))
            .setAuthentication(toJson(authorizationGrant.authentication()))
            .setClientId(authorizationGrant.clientId().value())
            .setScopes(authorizationGrant.scopes().toStringValues());
    if (authorizationGrant.hasClaim()) {
      builder.setClaims(toJson(authorizationGrant.claimsPayload()));
    }

    if (authorizationGrant.hasCustomProperties()) {
      builder.setCustomProperties(toJson(authorizationGrant.customProperties().values()));
    }
    if (authorizationGrant.hasAuthorizationDetails()) {
      builder.setAuthorizationDetails(
          toJson(authorizationGrant.authorizationDetails().toMapValues()));
    }

    return builder.build();
  }

  static String crateUpdate(CibaGrant cibaGrant) {
    String authentication = toJson(cibaGrant.authorizationGrant().authentication());
    String status = cibaGrant.status().name();
    String sqlTemplate =
        """
            UPDATE ciba_grant
            SET authentication = '%s',
            status = '%s'
            WHERE backchannel_authentication_request_id = '%s';
            """;
    return String.format(
        sqlTemplate,
        authentication,
        status,
        cibaGrant.backchannelAuthenticationRequestIdentifier().value());
  }

  private static String toJson(Object value) {
    return jsonParser.write(value);
  }
}
