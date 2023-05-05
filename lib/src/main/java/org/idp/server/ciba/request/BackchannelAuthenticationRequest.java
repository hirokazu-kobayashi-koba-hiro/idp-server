package org.idp.server.ciba.request;

import org.idp.server.ciba.CibaProfile;
import org.idp.server.type.ciba.*;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.Scopes;
import org.idp.server.type.oauth.TokenIssuer;
import org.idp.server.type.oidc.AcrValues;
import org.idp.server.type.oidc.IdTokenHint;
import org.idp.server.type.oidc.LoginHint;
import org.idp.server.type.oidc.RequestObject;

public class BackchannelAuthenticationRequest {
  BackchannelAuthenticationRequestIdentifier identifier;
  TokenIssuer tokenIssuer;
  CibaProfile profile;
  Scopes scopes;
  ClientId clientId;
  IdTokenHint idTokenHint;
  LoginHint loginHint;
  LoginHintToken loginHintToken;
  AcrValues acrValues;
  UserCode userCode;
  ClientNotificationToken clientNotificationToken;
  BindingMessage bindingMessage;
  RequestedExpiry requestedExpiry;
  RequestObject requestObject;

  BackchannelAuthenticationRequest(
      BackchannelAuthenticationRequestIdentifier identifier,
      TokenIssuer tokenIssuer,
      CibaProfile profile,
      Scopes scopes,
      ClientId clientId,
      IdTokenHint idTokenHint,
      LoginHint loginHint,
      LoginHintToken loginHintToken,
      AcrValues acrValues,
      UserCode userCode,
      ClientNotificationToken clientNotificationToken,
      BindingMessage bindingMessage,
      RequestedExpiry requestedExpiry,
      RequestObject requestObject) {
    this.identifier = identifier;
    this.tokenIssuer = tokenIssuer;
    this.profile = profile;
    this.scopes = scopes;
    this.clientId = clientId;
    this.idTokenHint = idTokenHint;
    this.loginHint = loginHint;
    this.loginHintToken = loginHintToken;
    this.acrValues = acrValues;
    this.userCode = userCode;
    this.clientNotificationToken = clientNotificationToken;
    this.bindingMessage = bindingMessage;
    this.requestedExpiry = requestedExpiry;
    this.requestObject = requestObject;
  }

  public BackchannelAuthenticationRequestIdentifier identifier() {
    return identifier;
  }

  public TokenIssuer tokenIssuer() {
    return tokenIssuer;
  }

  public CibaProfile profile() {
    return profile;
  }

  public Scopes scopes() {
    return scopes;
  }

  public boolean hasScopes() {
    return scopes.exists();
  }

  public ClientId clientId() {
    return clientId;
  }

  public boolean hasClientId() {
    return clientId.exists();
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
}
