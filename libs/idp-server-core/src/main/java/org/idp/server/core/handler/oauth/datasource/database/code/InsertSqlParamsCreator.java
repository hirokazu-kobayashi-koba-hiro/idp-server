package org.idp.server.core.handler.oauth.datasource.database.code;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.oauth.grant.AuthorizationCodeGrant;

class InsertSqlParamsCreator {

  static JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  static List<Object> create(AuthorizationCodeGrant authorizationCodeGrant) {
    List<Object> params = new ArrayList<>();

    params.add(authorizationCodeGrant.authorizationRequestIdentifier().value());
    params.add(authorizationCodeGrant.authorizationCode().value());
    params.add(authorizationCodeGrant.user().sub());
    params.add(toJson(authorizationCodeGrant.user()));
    params.add(toJson(authorizationCodeGrant.authentication()));
    params.add(authorizationCodeGrant.clientId().value());
    params.add(authorizationCodeGrant.scopes().toStringValues());

    if (authorizationCodeGrant.authorizationGrant().hasClaim()) {
      params.add(toJson(authorizationCodeGrant.authorizationGrant().claimsPayload()));
    } else {
      params.add("");
    }

    if (authorizationCodeGrant.authorizationGrant().hasCustomProperties()) {
      params.add(toJson(authorizationCodeGrant.authorizationGrant().customProperties().values()));
    } else {
      params.add("");
    }

    if (authorizationCodeGrant.authorizationGrant().hasAuthorizationDetails()) {
      params.add(
          toJson(authorizationCodeGrant.authorizationGrant().authorizationDetails().toMapValues()));
    } else {
      params.add("");
    }

    params.add(authorizationCodeGrant.expiredAt().toStringValue());

    if (authorizationCodeGrant.authorizationGrant().hasPresentationDefinition()) {
      params.add(toJson(authorizationCodeGrant.authorizationGrant().presentationDefinition()));
    } else {
      params.add("");
    }

    return params;
  }

  private static String toJson(Object value) {
    return jsonConverter.write(value);
  }
}
