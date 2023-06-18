package org.idp.server.handler.ciba.datasource.database.request;

import org.idp.server.basic.json.JsonParser;
import org.idp.server.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.oauth.rar.AuthorizationDetails;

public class InsertSqlCreator {

  static String createInsert(BackchannelAuthenticationRequest backchannelAuthenticationRequest) {
    InsertSqlBuilder builder =
        new InsertSqlBuilder(backchannelAuthenticationRequest.identifier().value())
            .setTokenIssuer(backchannelAuthenticationRequest.tokenIssuer().value())
            .setProfile(backchannelAuthenticationRequest.profile().name())
            .setDeliveryMode(backchannelAuthenticationRequest.deliveryMode().name())
            .setScopes(backchannelAuthenticationRequest.scopes().toStringValues())
            .setClientId(backchannelAuthenticationRequest.clientId().value());

    if (backchannelAuthenticationRequest.hasIdTokenHint()) {
      builder.setIdTokenHint(backchannelAuthenticationRequest.idTokenHint().value());
    }
    if (backchannelAuthenticationRequest.hasLoginHint()) {
      builder.setLoginHint(backchannelAuthenticationRequest.loginHint().value());
    }
    if (backchannelAuthenticationRequest.hasLoginHintToken()) {
      builder.setLoginHintToken(backchannelAuthenticationRequest.loginHintToken().value());
    }
    if (backchannelAuthenticationRequest.hasAcrValues()) {
      builder.setAcrValues(backchannelAuthenticationRequest.acrValues().toStringValues());
    }
    if (backchannelAuthenticationRequest.hasUserCode()) {
      builder.setBindMessage(backchannelAuthenticationRequest.bindingMessage().value());
    }
    if (backchannelAuthenticationRequest.hasClientNotificationToken()) {
      builder.setClientNotificationToken(
          backchannelAuthenticationRequest.clientNotificationToken().value());
    }
    if (backchannelAuthenticationRequest.hasRequestedExpiry()) {
      builder.setRequestedExpiry(backchannelAuthenticationRequest.requestedExpiry().value());
    }
    if (backchannelAuthenticationRequest.hasRequest()) {
      builder.setRequestObject(backchannelAuthenticationRequest.requestObject().value());
    }
    if (backchannelAuthenticationRequest.hasAuthorizationDetails()) {
      builder.setAuthorizationDetails(
          convertAuthorizationDetails(backchannelAuthenticationRequest.authorizationDetails()));
    }
    return builder.build();
  }

  private static String convertAuthorizationDetails(AuthorizationDetails authorizationDetails) {
    JsonParser jsonParser = JsonParser.createWithSnakeCaseStrategy();
    return jsonParser.write(authorizationDetails.toMapValues());
  }
}
