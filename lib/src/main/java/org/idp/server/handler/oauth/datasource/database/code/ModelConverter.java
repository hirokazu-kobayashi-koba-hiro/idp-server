package org.idp.server.handler.oauth.datasource.database.code;

import java.util.List;
import java.util.Map;
import org.idp.server.basic.json.JsonConverter;
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
import org.idp.server.verifiablepresentation.request.PresentationDefinition;

class ModelConverter {

  static JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  static AuthorizationCodeGrant convert(Map<String, String> stringMap) {
    AuthorizationRequestIdentifier id =
        new AuthorizationRequestIdentifier(stringMap.get("authorization_request_id"));
    User user = jsonConverter.read(stringMap.get("user_payload"), User.class);
    Authentication authentication =
        jsonConverter.read(stringMap.get("authentication"), Authentication.class);
    ClientId clientId = new ClientId(stringMap.get("client_id"));
    Scopes scopes = new Scopes(stringMap.get("scopes"));
    CustomProperties customProperties = new CustomProperties();
    ClaimsPayload claimsPayload = convertClaimsPayload(stringMap.get("claims"));
    AuthorizationDetails authorizationDetails =
        convertAuthorizationDetails(stringMap.get("authorization_details"));
    PresentationDefinition presentationDefinition =
        convertPresentationDefinition(stringMap.get("presentation_definition"));
    AuthorizationGrant authorizationGrant =
        new AuthorizationGrant(
            user,
            authentication,
            clientId,
            scopes,
            claimsPayload,
            customProperties,
            authorizationDetails,
            presentationDefinition);
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
      JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
      return jsonConverter.read(value, ClaimsPayload.class);
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
      JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
      List list = jsonConverter.read(value, List.class);
      List<Map> details = (List<Map>) list;
      List<AuthorizationDetail> authorizationDetailsList =
          details.stream().map(detail -> new AuthorizationDetail(detail)).toList();
      return new AuthorizationDetails(authorizationDetailsList);
    } catch (Exception exception) {
      return new AuthorizationDetails();
    }
  }

  private static PresentationDefinition convertPresentationDefinition(String value) {
    if (value.isEmpty()) {
      return new PresentationDefinition();
    }
    try {
      JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
      return jsonConverter.read(value, PresentationDefinition.class);
    } catch (Exception exception) {
      return new PresentationDefinition();
    }
  }
}
