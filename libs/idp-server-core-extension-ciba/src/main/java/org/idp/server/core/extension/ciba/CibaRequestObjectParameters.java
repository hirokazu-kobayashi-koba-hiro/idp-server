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

package org.idp.server.core.extension.ciba;

import static org.idp.server.core.openid.oauth.type.OAuthRequestKey.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.openid.oauth.clientauthenticator.BackchannelRequestParameters;
import org.idp.server.core.openid.oauth.rar.AuthorizationDetails;
import org.idp.server.core.openid.oauth.type.OAuthRequestKey;
import org.idp.server.core.openid.oauth.type.ciba.*;
import org.idp.server.core.openid.oauth.type.oauth.*;
import org.idp.server.core.openid.oauth.type.oidc.AcrValues;
import org.idp.server.core.openid.oauth.type.oidc.IdTokenHint;
import org.idp.server.core.openid.oauth.type.oidc.LoginHint;
import org.idp.server.core.openid.oauth.type.oidc.RequestObject;

public class CibaRequestObjectParameters implements BackchannelRequestParameters {
  Map<String, Object> values;

  public CibaRequestObjectParameters() {
    this.values = new HashMap<>();
  }

  public CibaRequestObjectParameters(Map<String, Object> values) {
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

  public ClientNotificationToken clientNotificationToken() {
    return new ClientNotificationToken(getValueOrEmpty(client_notification_token));
  }

  public boolean hasClientNotificationToken() {
    return contains(client_notification_token);
  }

  public UserCode userCode() {
    return new UserCode(getValueOrEmpty(user_code));
  }

  public boolean hasUserCode() {
    return contains(user_code);
  }

  public BindingMessage bindingMessage() {
    return new BindingMessage(getValueOrEmpty(binding_message));
  }

  public boolean hasBindingMessage() {
    return contains(binding_message);
  }

  public LoginHintToken loginHintToken() {
    return new LoginHintToken(getValueOrEmpty(login_hint_token));
  }

  public boolean hasLoginHintToken() {
    return contains(login_hint_token);
  }

  public RequestedExpiry requestedExpiry() {
    Long value = getValueAsLong(requested_expiry);
    if (value == null) return new RequestedExpiry();
    return new RequestedExpiry(value.toString());
  }

  public boolean hasRequestedExpiry() {
    return contains(requested_expiry);
  }

  public RequestedClientId clientId() {
    return new RequestedClientId(getValueOrEmpty(client_id));
  }

  public boolean hasClientId() {
    return contains(client_id);
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

  @Override
  public ClientSecret clientSecret() {
    return new ClientSecret(getValueOrEmpty(client_secret));
  }

  @Override
  public boolean hasClientSecret() {
    return contains(client_secret);
  }

  @Override
  public ClientAssertion clientAssertion() {
    return new ClientAssertion(getValueOrEmpty(client_assertion));
  }

  @Override
  public boolean hasClientAssertion() {
    return contains(client_assertion);
  }

  @Override
  public ClientAssertionType clientAssertionType() {
    return ClientAssertionType.of(getValueOrEmpty(client_assertion_type));
  }

  @Override
  public boolean hasClientAssertionType() {
    return contains(client_assertion_type);
  }

  String getValueOrEmpty(OAuthRequestKey key) {
    Object value = values.get(key.name());
    if (Objects.isNull(value)) {
      return "";
    }
    return (String) value;
  }

  /**
   * Gets a value as Long, accepting both JSON number and JSON string per CIBA-7.1.1.
   *
   * <p>Per CIBA Core Section 7.1.1, requested_expiry may be sent as either a JSON string or a JSON
   * number, and the OP must accept either type.
   *
   * @param key the OAuth request key
   * @return the value as Long, or null if not present
   */
  Long getValueAsLong(OAuthRequestKey key) {
    Object value = values.get(key.name());
    if (Objects.isNull(value)) {
      return null;
    }
    if (value instanceof Number) {
      return ((Number) value).longValue();
    }
    if (value instanceof String) {
      return Long.parseLong((String) value);
    }
    return (long) value;
  }

  boolean contains(OAuthRequestKey key) {
    return values.containsKey(key.name());
  }

  Map<String, Object> values() {
    return values;
  }

  public boolean hasAuthorizationDetails() {
    return contains(authorization_details);
  }

  public AuthorizationDetails authorizationDetails() {
    return AuthorizationDetails.fromObject(values.get(authorization_details.name()));
  }
}
