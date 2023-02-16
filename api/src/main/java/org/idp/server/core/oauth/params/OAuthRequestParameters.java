package org.idp.server.core.oauth.params;

import static org.idp.server.core.oauth.params.OAuthRequestKey.*;

import java.util.List;
import java.util.Map;

/** OAuthRequestParameters */
public class OAuthRequestParameters {
  MultiValueMap values;

  public OAuthRequestParameters() {
    this.values = new MultiValueMap();
  }

  public OAuthRequestParameters(MultiValueMap values) {
    this.values = values;
  }

  public OAuthRequestParameters(Map<String, String[]> values) {
    this.values = new MultiValueMap(values);
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }

  public String getScope() {
    return getString(scope);
  }

  public boolean hasScope() {
    return contains(scope);
  }

  public String getResponseType() {
    return getString(response_type);
  }

  public boolean hasResponseType() {
    return contains(response_type);
  }

  public String getClientId() {
    return getString(client_id);
  }

  public boolean hasClientId() {
    return contains(client_id);
  }

  public String getRedirectUri() {
    return getString(redirect_uri);
  }

  public boolean hasRedirectUri() {
    return contains(redirect_uri);
  }

  public String getState() {
    return getString(state);
  }

  public boolean hasState() {
    return contains(state);
  }

  public String getResponseMode() {
    return getString(response_mode);
  }

  public boolean hasResponseMode() {
    return contains(response_mode);
  }

  public String getNonce() {
    return getString(nonce);
  }

  public boolean hasNonce() {
    return contains(nonce);
  }

  public String getDisplay() {
    return getString(display);
  }

  public boolean hasDisplay() {
    return contains(display);
  }

  public String getPrompt() {
    return getString(prompt);
  }

  public boolean hasPrompt() {
    return contains(prompt);
  }

  public String getMaxAge() {
    return getString(max_age);
  }

  public boolean hasMaxAge() {
    return contains(max_age);
  }

  public String getUiLocales() {
    return getString(ui_locales);
  }

  public boolean hasUiLocales() {
    return contains(ui_locales);
  }

  public String getIdTokenHint() {
    return getString(id_token_hint);
  }

  public boolean hasIdTokenHint() {
    return contains(id_token_hint);
  }

  public String getLoginHint() {
    return getString(login_hint);
  }

  public boolean hasLoginHint() {
    return contains(login_hint);
  }

  public String getAcrValues() {
    return getString(acr_values);
  }

  public boolean hasAcrValues() {
    return contains(acr_values);
  }

  public String getClaims() {
    return getString(claims);
  }

  public boolean hasClaims() {
    return contains(claims);
  }

  public String getRequest() {
    return getString(request);
  }

  public boolean hasRequest() {
    return contains(request);
  }

  public String getRequestUri() {
    return getString(request_uri);
  }

  public boolean hasRequestUri() {
    return contains(request_uri);
  }

  String getString(OAuthRequestKey key) {
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
