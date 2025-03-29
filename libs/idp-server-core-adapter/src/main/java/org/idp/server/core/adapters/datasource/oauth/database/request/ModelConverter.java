package org.idp.server.core.adapters.datasource.oauth.database.request;

import java.util.List;
import java.util.Map;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.oauth.AuthorizationProfile;
import org.idp.server.core.oauth.client.Client;
import org.idp.server.core.oauth.identity.RequestedClaimsPayload;
import org.idp.server.core.oauth.rar.AuthorizationDetail;
import org.idp.server.core.oauth.rar.AuthorizationDetails;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.request.AuthorizationRequestBuilder;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.oauth.vp.request.PresentationDefinition;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.oauth.*;
import org.idp.server.core.type.oidc.*;
import org.idp.server.core.type.pkce.CodeChallenge;
import org.idp.server.core.type.pkce.CodeChallengeMethod;
import org.idp.server.core.type.verifiablepresentation.PresentationDefinitionUri;

class ModelConverter {

  static JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  static AuthorizationRequest convert(Map<String, String> stringMap) {
    AuthorizationRequestBuilder builder = new AuthorizationRequestBuilder();
    builder.add(new AuthorizationRequestIdentifier(stringMap.get("id")));
    builder.add(new TenantIdentifier(stringMap.get("tenant_id")));
    builder.add(AuthorizationProfile.valueOf(stringMap.get("profile")));
    builder.add(new Scopes(stringMap.get("scopes")));
    builder.add(ResponseType.valueOf(stringMap.get("response_type")));
    builder.add(new RequestedClientId(stringMap.get("client_id")));
    builder.add(convertClient(stringMap.get("client_payload")));
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
    builder.add(convertCustomParams(stringMap.get("custom_params")));
    return builder.build();
  }

  private static Client convertClient(String value) {
    if (value == null || value.isEmpty()) {
      return new Client();
    }
    return jsonConverter.read(value, Client.class);
  }

  private static RequestedClaimsPayload convertClaimsPayload(String value) {
    if (value == null || value.isEmpty()) {
      return new RequestedClaimsPayload();
    }
    try {
      return jsonConverter.read(value, RequestedClaimsPayload.class);
    } catch (Exception exception) {
      return new RequestedClaimsPayload();
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

  private static CustomParams convertCustomParams(String value) {
    if (value == null || value.isEmpty()) {
      return new CustomParams();
    }
    try {

      Map<String, String> read = jsonConverter.read(value, Map.class);

      return new CustomParams(read);
    } catch (Exception exception) {
      return new CustomParams();
    }
  }
}
