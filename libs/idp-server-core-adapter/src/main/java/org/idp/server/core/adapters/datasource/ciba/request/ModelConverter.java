package org.idp.server.core.adapters.datasource.ciba.request;

import java.util.List;
import java.util.Map;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.ciba.CibaProfile;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequestBuilder;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.oauth.rar.AuthorizationDetail;
import org.idp.server.core.oauth.rar.AuthorizationDetails;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.ciba.*;
import org.idp.server.core.type.oauth.RequestedClientId;
import org.idp.server.core.type.oauth.Scopes;
import org.idp.server.core.type.oidc.AcrValues;
import org.idp.server.core.type.oidc.IdTokenHint;
import org.idp.server.core.type.oidc.LoginHint;
import org.idp.server.core.type.oidc.RequestObject;

class ModelConverter {

  static BackchannelAuthenticationRequest convert(Map<String, String> stringMap) {
    BackchannelAuthenticationRequestBuilder builder = new BackchannelAuthenticationRequestBuilder();
    builder.add(new BackchannelAuthenticationRequestIdentifier(stringMap.get("id")));
    builder.add(new TenantIdentifier(stringMap.get("tenant_id")));
    builder.add(CibaProfile.valueOf(stringMap.get("profile")));
    builder.add(BackchannelTokenDeliveryMode.valueOf(stringMap.get("delivery_mode")));
    builder.add(new Scopes(stringMap.get("scopes")));
    builder.add(new RequestedClientId(stringMap.get("client_id")));
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
    if (value == null || value.isEmpty()) {
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
