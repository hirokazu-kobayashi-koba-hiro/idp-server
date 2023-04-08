package org.idp.server.core.type;

import static org.idp.server.core.type.OAuthRequestKey.*;

import java.util.List;
import java.util.Map;
import org.idp.server.core.type.oauth.*;
import org.idp.server.core.type.oidc.*;

/** OAuthRequestParameters */
public class OAuthRequestParameters {
  ArrayValueMap values;

  public OAuthRequestParameters() {
    this.values = new ArrayValueMap();
  }

  public OAuthRequestParameters(ArrayValueMap values) {
    this.values = values;
  }

  public OAuthRequestParameters(Map<String, String[]> values) {
    this.values = new ArrayValueMap(values);
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }

  public Scopes scope() {
    return new Scopes(getString(scope));
  }

  public boolean hasScope() {
    return contains(scope);
  }

  public ResponseType responseType() {
    return ResponseType.of(getString(response_type));
  }

  public boolean hasResponseType() {
    return contains(response_type);
  }

  public ClientId clientId() {
    return new ClientId(getString(client_id));
  }

  public boolean hasClientId() {
    return contains(client_id);
  }

  public RedirectUri redirectUri() {
    return new RedirectUri(getString(redirect_uri));
  }

  public boolean hasRedirectUri() {
    return contains(redirect_uri);
  }

  public State state() {
    return new State(getString(state));
  }

  public boolean hasState() {
    return contains(state);
  }

  public ResponseMode responseMode() {
    return ResponseMode.of(getString(response_mode));
  }

  public boolean hasResponseMode() {
    return contains(response_mode);
  }

  public Nonce nonce() {
    return new Nonce(getString(nonce));
  }

  public boolean hasNonce() {
    return contains(nonce);
  }

  public Display display() {
    return Display.of(getString(display));
  }

  public boolean hasDisplay() {
    return contains(display);
  }

  public Prompt prompt() {
    return Prompt.of(getString(prompt));
  }

  public boolean hasPrompt() {
    return contains(prompt);
  }

  public MaxAge maxAge() {
    return new MaxAge(getString(max_age));
  }

  public boolean hasMaxAge() {
    return contains(max_age);
  }

  public UiLocales uiLocales() {
    return new UiLocales(getString(ui_locales));
  }

  public boolean hasUiLocales() {
    return contains(ui_locales);
  }

  public IdTokenHint idTokenHint() {
    return new IdTokenHint(getString(id_token_hint));
  }

  public boolean hasIdTokenHint() {
    return contains(id_token_hint);
  }

  public LoginHint loginHint() {
    return new LoginHint(getString(login_hint));
  }

  public boolean hasLoginHint() {
    return contains(login_hint);
  }

  public AcrValues acrValues() {
    return new AcrValues(getString(acr_values));
  }

  public boolean hasAcrValues() {
    return contains(acr_values);
  }

  public Claims claims() {
    return new Claims(getString(claims));
  }

  public boolean hasClaims() {
    return contains(claims);
  }

  public RequestObject request() {
    return new RequestObject(getString(request));
  }

  public boolean hasRequest() {
    return contains(request);
  }

  public RequestUri requestUri() {
    return new RequestUri(getString(request_uri));
  }

  public boolean hasRequestUri() {
    return contains(request_uri);
  }

  public String getString(OAuthRequestKey key) {
    if (!values.contains(key.name())) {
      return "";
    }
    return values.getFirst(key.name());
  }

  boolean contains(OAuthRequestKey key) {
    return values.contains(key.name());
  }

  public List<String> multiValueKeys() {
    return values.multiValueKeys();
  }
}
