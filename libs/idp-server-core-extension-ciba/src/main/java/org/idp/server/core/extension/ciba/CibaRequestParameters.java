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

import static org.idp.server.basic.type.OAuthRequestKey.*;

import java.util.List;
import java.util.Map;
import org.idp.server.basic.type.ArrayValueMap;
import org.idp.server.basic.type.OAuthRequestKey;
import org.idp.server.basic.type.ciba.*;
import org.idp.server.basic.type.oauth.*;
import org.idp.server.basic.type.oidc.*;
import org.idp.server.core.oidc.clientauthenticator.BackchannelRequestParameters;
import org.idp.server.core.oidc.rar.AuthorizationDetails;

public class CibaRequestParameters implements BackchannelRequestParameters {
  ArrayValueMap values;

  public CibaRequestParameters() {
    this.values = new ArrayValueMap();
  }

  public CibaRequestParameters(ArrayValueMap values) {
    this.values = values;
  }

  public CibaRequestParameters(Map<String, String[]> values) {
    this.values = new ArrayValueMap(values);
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
    return new RequestedExpiry(getValueOrEmpty(requested_expiry));
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

  public String getValueOrEmpty(OAuthRequestKey key) {
    return values.getFirstOrEmpty(key.name());
  }

  boolean contains(OAuthRequestKey key) {
    return values.contains(key.name());
  }

  public List<String> multiValueKeys() {
    return values.multiValueKeys();
  }

  public Map<String, String> singleValues() {
    return values.singleValueMap();
  }

  public CibaRequestPattern analyze() {
    if (hasRequest()) {
      return CibaRequestPattern.REQUEST_OBJECT;
    }
    return CibaRequestPattern.NORMAL;
  }

  public AuthorizationDetails authorizationDetails() {
    return AuthorizationDetails.fromString(getValueOrEmpty(authorization_details));
  }

  public boolean hasAuthorizationDetails() {
    return contains(authorization_details);
  }
}
