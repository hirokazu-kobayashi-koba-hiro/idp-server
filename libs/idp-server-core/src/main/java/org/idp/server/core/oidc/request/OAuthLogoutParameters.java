/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.idp.server.core.oidc.request;

import static org.idp.server.basic.type.OAuthRequestKey.*;

import java.util.List;
import java.util.Map;
import org.idp.server.basic.type.ArrayValueMap;
import org.idp.server.basic.type.OAuthRequestKey;
import org.idp.server.basic.type.oauth.*;
import org.idp.server.basic.type.oidc.*;
import org.idp.server.basic.type.oidc.logout.LogoutHint;
import org.idp.server.basic.type.oidc.logout.PostLogoutRedirectUri;

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

  public RequestedClientId clientId() {
    return new RequestedClientId(getValueOrEmpty(client_id));
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
