package org.idp.server.core.oauth.request;

import static org.idp.server.core.type.OAuthRequestKey.*;

import java.util.List;
import java.util.Map;
import org.idp.server.core.type.ArrayValueMap;
import org.idp.server.core.type.OAuthRequestKey;
import org.idp.server.core.type.oauth.*;
import org.idp.server.core.type.oidc.*;
import org.idp.server.core.type.oidc.logout.LogoutHint;
import org.idp.server.core.type.oidc.logout.PostLogoutRedirectUri;

/** OAuthLogoutParameters */
public class OAuthLogoutParameters {
  ArrayValueMap values;

  public OAuthLogoutParameters() {
    this.values = new ArrayValueMap();
  }

  public OAuthLogoutParameters(ArrayValueMap values) {
    this.values = values;
  }

  public OAuthLogoutParameters(Map<String, String[]> values) {
    this.values = new ArrayValueMap(values);
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }

  public IdTokenHint idTokenHint() {
    return new IdTokenHint(getValueOrEmpty(id_token_hint));
  }

  public boolean hasIdTokenHint() {
    return contains(id_token_hint);
  }

  public LogoutHint logoutHint() {
    return new LogoutHint(getValueOrEmpty(logout_hint));
  }

  public boolean hasLogoutHint() {
    return contains(logout_hint);
  }

  public ClientId clientId() {
    return new ClientId(getValueOrEmpty(client_id));
  }

  public boolean hasClientId() {
    return contains(client_id);
  }

  public PostLogoutRedirectUri postLogoutRedirectUri() {
    return new PostLogoutRedirectUri(getValueOrEmpty(post_logout_redirect_uri));
  }

  public boolean hasPostLogoutRedirectUri() {
    return contains(post_logout_redirect_uri);
  }

  public State state() {
    return new State(getValueOrEmpty(state));
  }

  public boolean hasState() {
    return contains(state);
  }

  public UiLocales uiLocales() {
    return new UiLocales(getValueOrEmpty(ui_locales));
  }

  public boolean hasUiLocales() {
    return contains(ui_locales);
  }

  public String getValueOrEmpty(OAuthRequestKey key) {
    return values.getFirstOrEmpty(key.name());
  }

  boolean contains(OAuthRequestKey key) {
    return values.contains(key.name());
  }

  public List<String> multiValueKeys() {
    return values.multiValueKeys();
  }
}
