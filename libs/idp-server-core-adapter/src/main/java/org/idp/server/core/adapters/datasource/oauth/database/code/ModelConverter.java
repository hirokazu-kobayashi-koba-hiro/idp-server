package org.idp.server.core.adapters.datasource.oauth.database.code;

import java.util.List;
import java.util.Map;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.core.oauth.grant.AuthorizationGrant;
import org.idp.server.core.oauth.identity.ClaimsPayload;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.rar.AuthorizationDetail;
import org.idp.server.core.oauth.rar.AuthorizationDetails;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.oauth.vp.request.PresentationDefinition;
import org.idp.server.core.type.extension.CustomProperties;
import org.idp.server.core.type.extension.ExpiredAt;
import org.idp.server.core.type.oauth.AuthorizationCode;
import org.idp.server.core.type.oauth.ClientId;
import org.idp.server.core.type.oauth.Scopes;

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
    CustomProperties customProperties = convertCustomProperties(stringMap.get("custom_properties"));
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
    if (value == null || value.isEmpty()) {
      return new ClaimsPayload();
    }
    try {

      return jsonConverter.read(value, ClaimsPayload.class);
    } catch (Exception exception) {
      return new ClaimsPayload();
    }
  }

  private static CustomProperties convertCustomProperties(String value) {
    if (value == null || value.isEmpty()) {
      return new CustomProperties();
    }
    try {

      Map map = jsonConverter.read(value, Map.class);

      return new CustomProperties(map);
    } catch (Exception exception) {
      return new CustomProperties();
    }
  }

  private static AuthorizationDetails convertAuthorizationDetails(String value) {
    if (value == null || value.isEmpty()) {
      return new AuthorizationDetails();
    }
    try {

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
    if (value == null || value.isEmpty()) {
      return new PresentationDefinition();
    }
    try {

      return jsonConverter.read(value, PresentationDefinition.class);
    } catch (Exception exception) {
      return new PresentationDefinition();
    }
  }
}
