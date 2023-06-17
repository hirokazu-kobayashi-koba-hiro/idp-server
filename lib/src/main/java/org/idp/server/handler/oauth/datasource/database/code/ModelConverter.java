package org.idp.server.handler.oauth.datasource.database.code;

import java.util.List;
import java.util.Map;
import org.idp.server.basic.json.JsonParser;
import org.idp.server.oauth.authentication.Authentication;
import org.idp.server.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.oauth.grant.AuthorizationGrant;
import org.idp.server.oauth.identity.ClaimsPayload;
import org.idp.server.oauth.identity.User;
import org.idp.server.oauth.rar.AuthorizationDetail;
import org.idp.server.oauth.rar.AuthorizationDetails;
import org.idp.server.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.type.extension.CustomProperties;
import org.idp.server.type.extension.ExpiredAt;
import org.idp.server.type.oauth.*;
import org.idp.server.type.oidc.*;

class ModelConverter {

  static JsonParser jsonParser = JsonParser.createWithSnakeCaseStrategy();

  static AuthorizationCodeGrant convert(Map<String, String> stringMap) {
    AuthorizationRequestIdentifier id =
        new AuthorizationRequestIdentifier(stringMap.get("authorization_request_id"));
    User user = jsonParser.read(stringMap.get("user_payload"), User.class);
    Authentication authentication =
        jsonParser.read(stringMap.get("authentication"), Authentication.class);
    ClientId clientId = new ClientId(stringMap.get("client_id"));
    Scopes scopes = new Scopes(stringMap.get("scopes"));
    CustomProperties customProperties = new CustomProperties();
    ClaimsPayload claimsPayload = convertClaimsPayload(stringMap.get("claims"));
    AuthorizationDetails authorizationDetails =
        convertAuthorizationDetails(stringMap.get("authorization_details"));
    AuthorizationGrant authorizationGrant =
        new AuthorizationGrant(
            user,
            authentication,
            clientId,
            scopes,
            claimsPayload,
            customProperties,
            authorizationDetails);
    AuthorizationCode authorizationCode =
        new AuthorizationCode(stringMap.get("authorization_code"));
    ExpiredAt expiredAt = new ExpiredAt(stringMap.get("expired_at"));
    return new AuthorizationCodeGrant(id, authorizationGrant, authorizationCode, expiredAt);
  }

  private static ClaimsPayload convertClaimsPayload(String value) {
    if (value.isEmpty()) {
      return new ClaimsPayload();
    }
    try {
      JsonParser jsonParser = JsonParser.createWithSnakeCaseStrategy();
      return jsonParser.read(value, ClaimsPayload.class);
    } catch (Exception exception) {
      return new ClaimsPayload();
    }
  }

  // TODO
  private static AuthorizationDetails convertAuthorizationDetails(String value) {
    if (value.isEmpty()) {
      return new AuthorizationDetails();
    }
    try {
      JsonParser jsonParser = JsonParser.createWithSnakeCaseStrategy();
      List list = jsonParser.read(value, List.class);
      List<Map> details = (List<Map>) list;
      List<AuthorizationDetail> authorizationDetailsList =
          details.stream().map(detail -> new AuthorizationDetail(detail)).toList();
      return new AuthorizationDetails(authorizationDetailsList);
    } catch (Exception exception) {
      return new AuthorizationDetails();
    }
  }
}
