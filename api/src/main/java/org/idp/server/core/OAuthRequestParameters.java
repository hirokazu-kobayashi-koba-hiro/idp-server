package org.idp.server.core;

import static org.idp.server.core.OAuthRequestKey.*;

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

  public String scope() {
    return getString(scope);
  }

  public String responseType() {
    return getString(response_type);
  }

  public String clientId() {
    return getString(client_id);
  }

  public String redirectUri() {
    return getString(redirect_uri);
  }

  public String state() {
    return getString(state);
  }

  public String responseMode() {
    return getString(response_mode);
  }

  public String nonce() {
    return getString(nonce);
  }

  public String display() {
    return getString(display);
  }

  public String prompt() {
    return getString(prompt);
  }

  public String maxAge() {
    return getString(max_age);
  }

  public String uiLocales() {
    return getString(ui_locales);
  }

  public String idTokenJint() {
    return getString(id_token_hint);
  }

  public String loginHint() {
    return getString(login_hint);
  }

  public String acrValues() {
    return getString(acr_values);
  }

  public String claims() {
    return getString(claims);
  }

  String getString(OAuthRequestKey key) {
    if (!values.contains(key.name())) {
      return "";
    }
    return values.getFirst(key.name());
  }

  public List<String> multiValueKeys() {
    return values.multiValueKeys();
  }
}
