package org.idp.server.handler.ciba.datasource.database.request;

import java.util.List;
import java.util.Map;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.ciba.CibaProfile;
import org.idp.server.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.ciba.request.BackchannelAuthenticationRequestBuilder;
import org.idp.server.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.oauth.rar.AuthorizationDetail;
import org.idp.server.oauth.rar.AuthorizationDetails;
import org.idp.server.type.ciba.*;
import org.idp.server.type.oauth.*;
import org.idp.server.type.oidc.*;

class ModelConverter {

  static BackchannelAuthenticationRequest convert(Map<String, String> stringMap) {
    BackchannelAuthenticationRequestBuilder builder = new BackchannelAuthenticationRequestBuilder();
    builder.add(new BackchannelAuthenticationRequestIdentifier(stringMap.get("id")));
    builder.add(new TokenIssuer(stringMap.get("token_issuer")));
    builder.add(CibaProfile.valueOf(stringMap.get("profile")));
    builder.add(BackchannelTokenDeliveryMode.valueOf(stringMap.get("delivery_mode")));
    builder.add(new Scopes(stringMap.get("scopes")));
    builder.add(new ClientId(stringMap.get("client_id")));
    builder.add(new IdTokenHint(stringMap.get("id_token_hint")));
    builder.add(new LoginHint(stringMap.get("login_hint")));
    builder.add(new LoginHintToken(stringMap.get("login_hint_token")));
    builder.add(new AcrValues(stringMap.get("acr_values")));
    builder.add(new UserCode(stringMap.get("user_code")));
    builder.add(new ClientNotificationToken(stringMap.get("client_notification_token")));
    builder.add(new BindingMessage(stringMap.get("binding_message")));
    builder.add(new RequestedExpiry(stringMap.get("requested_expiry")));
    builder.add(new RequestObject(stringMap.get("request_object")));
    builder.add(convertAuthorizationDetails(stringMap.get("authorization_details")));
    return builder.build();
  }

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
}
