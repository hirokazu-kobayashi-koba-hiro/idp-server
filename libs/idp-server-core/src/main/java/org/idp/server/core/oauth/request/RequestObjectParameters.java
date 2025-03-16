package org.idp.server.core.oauth.request;

import static org.idp.server.core.type.OAuthRequestKey.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.type.OAuthRequestKey;
import org.idp.server.core.type.oauth.*;
import org.idp.server.core.type.oidc.*;
import org.idp.server.core.type.pkce.CodeChallenge;
import org.idp.server.core.type.pkce.CodeChallengeMethod;
import org.idp.server.core.type.rar.AuthorizationDetailsEntity;
import org.idp.server.core.type.verifiablepresentation.PresentationDefinitionEntity;
import org.idp.server.core.type.verifiablepresentation.PresentationDefinitionUri;

/** RequestObjectParameters */
public class RequestObjectParameters {
  Map<String, Object> values;

  public RequestObjectParameters() {
    this.values = new HashMap<>();
  }

  public RequestObjectParameters(Map<String, Object> values) {
    this.values = values;
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }

  public Scopes scope() {
    return new Scopes(getValueOrEmpty(scope));
  }

  public boolean hasScope() {
    return contains(scope);
  }

  public ResponseType responseType() {
    return ResponseType.of(getValueOrEmpty(response_type));
  }

  public boolean hasResponseType() {
    return contains(response_type);
  }

  public ClientId clientId() {
    return new ClientId(getValueOrEmpty(client_id));
  }

  public boolean hasClientId() {
    return contains(client_id);
  }

  public RedirectUri redirectUri() {
    return new RedirectUri(getValueOrEmpty(redirect_uri));
  }

  public boolean hasRedirectUri() {
    return contains(redirect_uri);
  }

  public State state() {
    return new State(getValueOrEmpty(state));
  }

  public boolean hasState() {
    return contains(state);
  }

  public ResponseMode responseMode() {
    return ResponseMode.of(getValueOrEmpty(response_mode));
  }

  public boolean hasResponseMode() {
    return contains(response_mode);
  }

  public Nonce nonce() {
    return new Nonce(getValueOrEmpty(nonce));
  }

  public boolean hasNonce() {
    return contains(nonce);
  }

  public Display display() {
    return Display.of(getValueOrEmpty(display));
  }

  public boolean hasDisplay() {
    return contains(display);
  }

  public Prompts prompts() {
    return Prompts.of(getValueOrEmpty(prompt));
  }

  public boolean hasPrompt() {
    return contains(prompt);
  }

  public MaxAge maxAge() {
    return new MaxAge(getValueOrEmpty(max_age));
  }

  public boolean hasMaxAge() {
    return contains(max_age);
  }

  public UiLocales uiLocales() {
    return new UiLocales(getValueOrEmpty(ui_locales));
  }

  public boolean hasUiLocales() {
    return contains(ui_locales);
  }

  public IdTokenHint idTokenHint() {
    return new IdTokenHint(getValueOrEmpty(id_token_hint));
  }

  public boolean hasIdTokenHint() {
    return contains(id_token_hint);
  }

  public LoginHint loginHint() {
    return new LoginHint(getValueOrEmpty(login_hint));
  }

  public boolean hasLoginHint() {
    return contains(login_hint);
  }

  public AcrValues acrValues() {
    return new AcrValues(getValueOrEmpty(acr_values));
  }

  public boolean hasAcrValues() {
    return contains(acr_values);
  }

  public ClaimsValue claims() {
    return new ClaimsValue(getValueOrEmpty(claims));
  }

  public boolean hasClaims() {
    return contains(claims);
  }

  public RequestObject request() {
    return new RequestObject(getValueOrEmpty(request));
  }

  public boolean hasRequest() {
    return contains(request);
  }

  public RequestUri requestUri() {
    return new RequestUri(getValueOrEmpty(request_uri));
  }

  public boolean hasRequestUri() {
    return contains(request_uri);
  }

  public CodeChallenge codeChallenge() {
    return new CodeChallenge(getValueOrEmpty(code_challenge));
  }

  public boolean hasCodeChallenge() {
    return contains(code_challenge);
  }

  public CodeChallengeMethod codeChallengeMethod() {
    return CodeChallengeMethod.of(getValueOrEmpty(code_challenge_method));
  }

  public boolean hasCodeChallengeMethod() {
    return contains(code_challenge_method);
  }

  String getValueOrEmpty(OAuthRequestKey key) {
    Object value = values.get(key.name());
    if (Objects.isNull(value)) {
      return "";
    }
    return (String) value;
  }

  List<String> getList(OAuthRequestKey key) {
    Object value = values.get(key.name());
    if (Objects.isNull(value)) {
      return List.of();
    }
    return (List<String>) value;
  }

  boolean contains(OAuthRequestKey key) {
    return values.containsKey(key.name());
  }

  public boolean hasAuthorizationDetailsValue() {
    return contains(authorization_details);
  }

  public AuthorizationDetailsEntity authorizationDetailsEntity() {
    return new AuthorizationDetailsEntity(getList(authorization_details));
  }

  public PresentationDefinitionEntity presentationDefinition() {
    return new PresentationDefinitionEntity(values.get(presentation_definition.name()));
  }

  public boolean hasPresentationDefinition() {
    return contains(presentation_definition);
  }

  public PresentationDefinitionUri presentationDefinitionUri() {
    return new PresentationDefinitionUri(getValueOrEmpty(presentation_definition_uri));
  }

  public boolean hasPresentationDefinitionUri() {
    return contains(presentation_definition_uri);
  }
}
