package org.idp.server.core.extension.ciba.request;

import org.idp.server.basic.type.ciba.*;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.basic.type.oauth.Scopes;
import org.idp.server.basic.type.oidc.AcrValues;
import org.idp.server.basic.type.oidc.IdTokenHint;
import org.idp.server.basic.type.oidc.LoginHint;
import org.idp.server.basic.type.oidc.RequestObject;
import org.idp.server.core.extension.ciba.CibaProfile;
import org.idp.server.core.extension.ciba.user.UserHint;
import org.idp.server.core.extension.ciba.user.UserHintType;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.oidc.rar.AuthorizationDetails;

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
      AuthorizationDetails authorizationDetails) {
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
