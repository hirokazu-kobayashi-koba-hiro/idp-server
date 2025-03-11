package org.idp.server.core.handler.oauth.datasource.database.request;

import java.util.List;
import java.util.Map;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.oauth.AuthorizationProfile;
import org.idp.server.core.oauth.identity.ClaimsPayload;
import org.idp.server.core.oauth.rar.AuthorizationDetail;
import org.idp.server.core.oauth.rar.AuthorizationDetails;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.request.AuthorizationRequestBuilder;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.oauth.vp.request.PresentationDefinition;
import org.idp.server.core.type.oauth.*;
import org.idp.server.core.type.oidc.*;
import org.idp.server.core.type.pkce.CodeChallenge;
import org.idp.server.core.type.pkce.CodeChallengeMethod;
import org.idp.server.core.type.verifiablepresentation.PresentationDefinitionUri;

class ModelConverter {

  static AuthorizationRequest convert(Map<String, String> stringMap) {
    AuthorizationRequestBuilder builder = new AuthorizationRequestBuilder();
    builder.add(new AuthorizationRequestIdentifier(stringMap.get("id")));
    builder.add(new TokenIssuer(stringMap.get("token_issuer")));
    builder.add(AuthorizationProfile.valueOf(stringMap.get("profile")));
    builder.add(new Scopes(stringMap.get("scopes")));
    builder.add(ResponseType.valueOf(stringMap.get("response_type")));
    builder.add(new ClientId(stringMap.get("client_id")));
    builder.add(new RedirectUri(stringMap.get("redirect_uri")));
    builder.add(new State(stringMap.get("state")));
    builder.add(ResponseMode.of(stringMap.get("response_mode")));
    builder.add(new Nonce(stringMap.get("nonce")));
    builder.add(Display.of(stringMap.get("display")));
    builder.add(Prompts.of(stringMap.get("prompts")));
    builder.add(new MaxAge(stringMap.get("max_age")));
    builder.add(new UiLocales(stringMap.get("ui_locales")));
    builder.add(new IdTokenHint(stringMap.get("id_token_hint")));
    builder.add(new LoginHint(stringMap.get("login_hint")));
    builder.add(new AcrValues(stringMap.get("acr_values")));
    builder.add(new ClaimsValue(stringMap.get("claims_value")));
    builder.add(new RequestObject(stringMap.get("request_object")));
    builder.add(new RequestUri(stringMap.get("request_uri")));
    builder.add(convertClaimsPayload(stringMap.get("claims_value")));
    builder.add(new CodeChallenge(stringMap.get("code_challenge")));
    builder.add(CodeChallengeMethod.of(stringMap.get("code_challenge_method")));
    builder.add(convertAuthorizationDetails(stringMap.get("authorization_details")));
    builder.add(convertPresentationDefinition(stringMap.get("presentation_definition")));
    builder.add(new PresentationDefinitionUri(stringMap.get("presentation_definition_uri")));
    builder.add(convertCustomParams(stringMap.get("custom_params")));
    return builder.build();
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

  private static CustomParams convertCustomParams(String value) {
    if (value.isEmpty()) {
      return new CustomParams();
    }
    try {

      JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
      Map<String, String> read = jsonConverter.read(value, Map.class);

      return new CustomParams(read);
    } catch (Exception exception) {
      return new CustomParams();
    }
  }
}
