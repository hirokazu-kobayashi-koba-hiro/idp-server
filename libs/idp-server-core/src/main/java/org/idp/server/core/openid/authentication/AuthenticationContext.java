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

package org.idp.server.core.openid.authentication;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.oauth.rar.AuthorizationDetails;
import org.idp.server.core.openid.oauth.type.ciba.BindingMessage;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.core.openid.oauth.type.oidc.AcrValues;

public class AuthenticationContext {
  AcrValues acrValues;
  Scopes scopes;
  BindingMessage bindingMessage;
  AuthorizationDetails authorizationDetails;
  AuthenticationCustomParams customParams = new AuthenticationCustomParams();

  public AuthenticationContext() {}

  public AuthenticationContext(
      AcrValues acrValues,
      Scopes scopes,
      BindingMessage bindingMessage,
      AuthorizationDetails authorizationDetails) {
    this.acrValues = acrValues;
    this.scopes = scopes;
    this.bindingMessage = bindingMessage;
    this.authorizationDetails = authorizationDetails;
  }

  public AuthenticationContext(
      AcrValues acrValues,
      Scopes scopes,
      BindingMessage bindingMessage,
      AuthorizationDetails authorizationDetails,
      AuthenticationCustomParams customParams) {
    this(acrValues, scopes, bindingMessage, authorizationDetails);
    this.customParams = customParams;
  }

  public AcrValues acrValues() {
    return acrValues;
  }

  public Scopes scopes() {
    return scopes;
  }

  public BindingMessage bindingMessage() {
    return bindingMessage;
  }

  public AuthorizationDetails authorizationDetails() {
    return authorizationDetails;
  }

  /** Issue #1907: the custom parameters of the authorization request, with their sources. */
  public AuthenticationCustomParams customParams() {
    return customParams;
  }

  /** Stored with the transaction, and returned by the management API. */
  public Map<String, Object> toMap() {
    Map<String, Object> map = toMapForPublic();
    if (customParams.exists()) map.put("custom_params", customParams.toMap());
    return map;
  }

  /**
   * For the APIs an end-user's device calls. Leaves out the custom parameters, which the relying
   * party addressed to the server, not to the device.
   */
  public Map<String, Object> toMapForPublic() {
    HashMap<String, Object> map = new HashMap<>();
    if (acrValues.exists()) map.put("acr_values", acrValues.toStringValues());
    if (scopes.exists()) map.put("scopes", scopes.toStringValues());
    if (bindingMessage.exists()) map.put("binding_message", bindingMessage.value());
    if (authorizationDetails.exists())
      map.put("authorization_details", authorizationDetails.toMapValues());
    return map;
  }

  public boolean exists() {
    return acrValues != null
        || scopes != null
        || bindingMessage != null
        || authorizationDetails != null
        || customParams.exists();
  }
}
