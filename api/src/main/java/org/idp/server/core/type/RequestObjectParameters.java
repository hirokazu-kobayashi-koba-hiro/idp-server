package org.idp.server.core.type;

import static org.idp.server.core.type.OAuthRequestKey.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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

  public String scope() {
    return getString(scope);
  }

  public boolean hasScope() {
    return contains(scope);
  }

  public String responseType() {
    return getString(response_type);
  }

  public boolean hasResponseType() {
    return contains(response_type);
  }

  public String clientId() {
    return getString(client_id);
  }

  public boolean hasClientId() {
    return contains(client_id);
  }

  public String redirectUri() {
    return getString(redirect_uri);
  }

  public boolean hasRedirectUri() {
    return contains(redirect_uri);
  }

  public String state() {
    return getString(state);
  }

  public boolean hasState() {
    return contains(state);
  }

  public String responseMode() {
    return getString(response_mode);
  }

  public boolean hasResponseMode() {
    return contains(response_mode);
  }

  public String nonce() {
    return getString(nonce);
  }

  public boolean hasNonce() {
    return contains(nonce);
  }

  public String display() {
    return getString(display);
  }

  public boolean hasDisplay() {
    return contains(display);
  }

  public String prompt() {
    return getString(prompt);
  }

  public boolean hasPrompt() {
    return contains(prompt);
  }

  public String maxAge() {
    return getString(max_age);
  }

  public boolean hasMaxAge() {
    return contains(max_age);
  }

  public String uiLocales() {
    return getString(ui_locales);
  }

  public boolean hasUiLocales() {
    return contains(ui_locales);
  }

  public String idTokenHint() {
    return getString(id_token_hint);
  }

  public boolean hasIdTokenHint() {
    return contains(id_token_hint);
  }

  public String loginHint() {
    return getString(login_hint);
  }

  public boolean hasLoginHint() {
    return contains(login_hint);
  }

  public String acrValues() {
    return getString(acr_values);
  }

  public boolean hasAcrValues() {
    return contains(acr_values);
  }

  public String claims() {
    return getString(claims);
  }

  public boolean hasClaims() {
    return contains(claims);
  }

  public String request() {
    return getString(request);
  }

  public boolean hasRequest() {
    return contains(request);
  }

  public String requestUri() {
    return getString(request_uri);
  }

  public boolean hasRequestUri() {
    return contains(request_uri);
  }

  String getString(OAuthRequestKey key) {
    Object value = values.get(key.name());
    if (Objects.isNull(value)) {
      return "";
    }
    return (String) value;
  }

  boolean contains(OAuthRequestKey key) {
    return values.containsKey(key.name());
  }
}
