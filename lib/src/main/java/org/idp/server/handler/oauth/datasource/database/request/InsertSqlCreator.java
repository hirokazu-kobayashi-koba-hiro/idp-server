package org.idp.server.handler.oauth.datasource.database.request;

import org.idp.server.basic.json.JsonConverter;
import org.idp.server.oauth.rar.AuthorizationDetails;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.oauth.vp.request.PresentationDefinition;
import org.idp.server.type.oauth.CustomParams;

public class InsertSqlCreator {

  static String createInsert(AuthorizationRequest authorizationRequest) {
    InsertSqlBuilder builder = new InsertSqlBuilder(authorizationRequest.identifier().value());
    if (authorizationRequest.hasTokenIssuer()) {
      builder.setTokenIssuer(authorizationRequest.tokenIssuer().value());
    }
    if (authorizationRequest.hasProfile()) {
      builder.setProfile(authorizationRequest.profile().name());
    }
    if (authorizationRequest.hasScope()) {
      builder.setScopes(authorizationRequest.scope().toStringValues());
    }
    if (authorizationRequest.hasResponseType()) {
      builder.setResponseType(authorizationRequest.responseType().name());
    }
    if (authorizationRequest.hasClientId()) {
      builder.setClientId(authorizationRequest.clientId().value());
    }
    if (authorizationRequest.hasRedirectUri()) {
      builder.setRedirectUri(authorizationRequest.redirectUri().value());
    }
    if (authorizationRequest.hasState()) {
      builder.setState(authorizationRequest.state().value());
    }
    if (authorizationRequest.hasResponseMode()) {
      builder.setResponseMode(authorizationRequest.responseMode().name());
    }
    if (authorizationRequest.hasNonce()) {
      builder.setNonce(authorizationRequest.nonce().value());
    }
    if (authorizationRequest.hasDisplay()) {
      builder.setDisplay(authorizationRequest.display().name());
    }
    if (authorizationRequest.hasPrompts()) {
      builder.setPrompts(authorizationRequest.prompts().toStringValues());
    }
    if (authorizationRequest.hasMaxAge()) {
      builder.setMaxAge(authorizationRequest.maxAge().value());
    }
    if (authorizationRequest.hasUilocales()) {
      builder.setUiLocales(authorizationRequest.uiLocales().toStringValues());
    }
    if (authorizationRequest.hasIdTokenHint()) {
      builder.setIdTokenHint(authorizationRequest.idTokenHint().value());
    }
    if (authorizationRequest.hasLoginHint()) {
      builder.setLoginHint(authorizationRequest.loginHint().value());
    }
    if (authorizationRequest.hasAcrValues()) {
      builder.setAcrValues(authorizationRequest.acrValues().toStringValues());
    }
    if (authorizationRequest.hasClaims()) {
      builder.setClaimsValue(authorizationRequest.claims().value());
    }
    if (authorizationRequest.hasRequest()) {
      builder.setRequestObject(authorizationRequest.request().value());
    }
    if (authorizationRequest.hasRequestUri()) {
      builder.setRequestUri(authorizationRequest.requestUri().value());
    }
    if (authorizationRequest.hasCodeChallenge()) {
      builder.setCodeChallenge(authorizationRequest.codeChallenge().value());
    }
    if (authorizationRequest.hasCodeChallengeMethod()) {
      builder.setCodeChallengeMethod(authorizationRequest.codeChallengeMethod().name());
    }
    if (authorizationRequest.hasAuthorizationDetails()) {
      builder.setAuthorizationDetails(
          convertJsonAuthorizationDetails(authorizationRequest.authorizationDetails()));
    }
    if (authorizationRequest.hasPresentationDefinition()) {
      builder.setPresentationDefinition(
          convertJsonPresentationDefinition(authorizationRequest.presentationDefinition()));
    }
    if (authorizationRequest.hasPresentationDefinitionUri()) {
      builder.setPresentationDefinitionUri(
          authorizationRequest.presentationDefinitionUri().value());
    }
    if (authorizationRequest.hasCustomParams()) {
      builder.setCustomParams(convertJson(authorizationRequest.customParams()));
    }
    return builder.build();
  }

  private static String convertJsonAuthorizationDetails(AuthorizationDetails authorizationDetails) {
    JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
    return jsonConverter.write(authorizationDetails.toMapValues());
  }

  private static String convertJsonPresentationDefinition(
      PresentationDefinition presentationDefinition) {
    JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
    return jsonConverter.write(presentationDefinition);
  }

  private static String convertJson(CustomParams customParams) {
    JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
    return jsonConverter.write(customParams.values());
  }
}
