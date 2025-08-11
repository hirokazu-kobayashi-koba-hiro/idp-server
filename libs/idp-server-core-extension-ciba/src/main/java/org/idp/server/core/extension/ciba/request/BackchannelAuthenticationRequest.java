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

package org.idp.server.core.extension.ciba.request;

import org.idp.server.core.extension.ciba.CibaProfile;
import org.idp.server.core.extension.ciba.user.UserHint;
import org.idp.server.core.extension.ciba.user.UserHintType;
import org.idp.server.core.openid.oauth.rar.AuthorizationDetails;
import org.idp.server.core.openid.oauth.type.ciba.*;
import org.idp.server.core.openid.oauth.type.extension.ExpiresAt;
import org.idp.server.core.openid.oauth.type.oauth.ExpiresIn;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.core.openid.oauth.type.oidc.AcrValues;
import org.idp.server.core.openid.oauth.type.oidc.IdTokenHint;
import org.idp.server.core.openid.oauth.type.oidc.LoginHint;
import org.idp.server.core.openid.oauth.type.oidc.RequestObject;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class BackchannelAuthenticationRequest {
  BackchannelAuthenticationRequestIdentifier identifier;
  TenantIdentifier tenantIdentifier;
  CibaProfile profile;
  BackchannelTokenDeliveryMode deliveryMode;
  Scopes scopes;
  RequestedClientId requestedClientId;
  IdTokenHint idTokenHint;
  LoginHint loginHint;
  LoginHintToken loginHintToken;
  AcrValues acrValues;
  UserCode userCode;
  ClientNotificationToken clientNotificationToken;
  BindingMessage bindingMessage;
  RequestedExpiry requestedExpiry;
  RequestObject requestObject;
  AuthorizationDetails authorizationDetails;
  ExpiresIn expiresIn;
  ExpiresAt expiresAt;

  BackchannelAuthenticationRequest(
      BackchannelAuthenticationRequestIdentifier identifier,
      TenantIdentifier tenantIdentifier,
      CibaProfile profile,
      BackchannelTokenDeliveryMode deliveryMode,
      Scopes scopes,
      RequestedClientId requestedClientId,
      IdTokenHint idTokenHint,
      LoginHint loginHint,
      LoginHintToken loginHintToken,
      AcrValues acrValues,
      UserCode userCode,
      ClientNotificationToken clientNotificationToken,
      BindingMessage bindingMessage,
      RequestedExpiry requestedExpiry,
      RequestObject requestObject,
      AuthorizationDetails authorizationDetails,
      ExpiresIn expiresIn,
      ExpiresAt expiresAt) {
    this.identifier = identifier;
    this.tenantIdentifier = tenantIdentifier;
    this.profile = profile;
    this.deliveryMode = deliveryMode;
    this.scopes = scopes;
    this.requestedClientId = requestedClientId;
    this.idTokenHint = idTokenHint;
    this.loginHint = loginHint;
    this.loginHintToken = loginHintToken;
    this.acrValues = acrValues;
    this.userCode = userCode;
    this.clientNotificationToken = clientNotificationToken;
    this.bindingMessage = bindingMessage;
    this.requestedExpiry = requestedExpiry;
    this.requestObject = requestObject;
    this.authorizationDetails = authorizationDetails;
    this.expiresIn = expiresIn;
    this.expiresAt = expiresAt;
  }

  public BackchannelAuthenticationRequestIdentifier identifier() {
    return identifier;
  }

  public TenantIdentifier tenantIdentifier() {
    return tenantIdentifier;
  }

  public CibaProfile profile() {
    return profile;
  }

  public BackchannelTokenDeliveryMode deliveryMode() {
    return deliveryMode;
  }

  public Scopes scopes() {
    return scopes;
  }

  public boolean hasScopes() {
    return scopes.exists();
  }

  public RequestedClientId requestedClientId() {
    return requestedClientId;
  }

  public boolean hasClientId() {
    return requestedClientId.exists();
  }

  public IdTokenHint idTokenHint() {
    return idTokenHint;
  }

  public boolean hasIdTokenHint() {
    return idTokenHint.exists();
  }

  public LoginHint loginHint() {
    return loginHint;
  }

  public boolean hasLoginHint() {
    return loginHint.exists();
  }

  public LoginHintToken loginHintToken() {
    return loginHintToken;
  }

  public boolean hasLoginHintToken() {
    return loginHintToken.exists();
  }

  public AcrValues acrValues() {
    return acrValues;
  }

  public boolean hasAcrValues() {
    return acrValues.exists();
  }

  public UserCode userCode() {
    return userCode;
  }

  public boolean hasUserCode() {
    return userCode.exists();
  }

  public ClientNotificationToken clientNotificationToken() {
    return clientNotificationToken;
  }

  public boolean hasClientNotificationToken() {
    return clientNotificationToken.exists();
  }

  public BindingMessage bindingMessage() {
    return bindingMessage;
  }

  public boolean hasBindingMessage() {
    return bindingMessage.exists();
  }

  public RequestedExpiry requestedExpiry() {
    return requestedExpiry;
  }

  public boolean hasRequestedExpiry() {
    return requestedExpiry.exists();
  }

  public RequestObject requestObject() {
    return requestObject;
  }

  public ExpiresIn expiresIn() {
    return expiresIn;
  }

  public ExpiresAt expiresAt() {
    return expiresAt;
  }

  public boolean hasAnyHint() {
    return hasLoginHint() || hasIdTokenHint() || hasIdTokenHint();
  }

  public boolean isPingMode() {
    return deliveryMode.isPingMode();
  }

  public boolean isPushMode() {
    return deliveryMode.isPushMode();
  }

  public boolean hasRequest() {
    return requestObject.exists();
  }

  public AuthorizationDetails authorizationDetails() {
    return authorizationDetails;
  }

  public boolean hasAuthorizationDetails() {
    return authorizationDetails.exists();
  }

  public UserHintType userHintType() {
    if (hasIdTokenHint()) {
      return UserHintType.ID_TOKEN_HINT;
    }
    if (hasLoginHintToken()) {
      return UserHintType.LOGIN_HINT_TOKEN;
    }
    return UserHintType.LOGIN_HINT;
  }

  public UserHint userHint() {
    if (hasIdTokenHint()) {
      return new UserHint(idTokenHint.value());
    }
    if (hasLoginHintToken()) {
      return new UserHint(loginHintToken.value());
    }
    return new UserHint(loginHint.value());
  }
}
