/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.ciba.request;

import org.idp.server.basic.type.ciba.*;
import org.idp.server.basic.type.oauth.*;
import org.idp.server.basic.type.oidc.*;
import org.idp.server.core.extension.ciba.CibaProfile;
import org.idp.server.core.oidc.rar.AuthorizationDetails;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class BackchannelAuthenticationRequestBuilder {

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
  AuthorizationDetails authorizationDetails = new AuthorizationDetails();

  public BackchannelAuthenticationRequestBuilder() {}

  public BackchannelAuthenticationRequestBuilder add(
      BackchannelAuthenticationRequestIdentifier identifier) {
    this.identifier = identifier;
    return this;
  }

  public BackchannelAuthenticationRequestBuilder add(TenantIdentifier tenantIdentifier) {
    this.tenantIdentifier = tenantIdentifier;
    return this;
  }

  public BackchannelAuthenticationRequestBuilder add(CibaProfile profile) {
    this.profile = profile;
    return this;
  }

  public BackchannelAuthenticationRequestBuilder add(BackchannelTokenDeliveryMode deliveryMode) {
    this.deliveryMode = deliveryMode;
    return this;
  }

  public BackchannelAuthenticationRequestBuilder add(AcrValues acrValues) {
    this.acrValues = acrValues;
    return this;
  }

  public BackchannelAuthenticationRequestBuilder add(RequestedClientId requestedClientId) {
    this.requestedClientId = requestedClientId;
    return this;
  }

  public BackchannelAuthenticationRequestBuilder add(IdTokenHint idTokenHint) {
    this.idTokenHint = idTokenHint;
    return this;
  }

  public BackchannelAuthenticationRequestBuilder add(LoginHint loginHint) {
    this.loginHint = loginHint;
    return this;
  }

  public BackchannelAuthenticationRequestBuilder add(LoginHintToken loginHintToken) {
    this.loginHintToken = loginHintToken;
    return this;
  }

  public BackchannelAuthenticationRequestBuilder add(RequestObject requestObject) {
    this.requestObject = requestObject;
    return this;
  }

  public BackchannelAuthenticationRequestBuilder add(Scopes scopes) {
    this.scopes = scopes;
    return this;
  }

  public BackchannelAuthenticationRequestBuilder add(UserCode userCode) {
    this.userCode = userCode;
    return this;
  }

  public BackchannelAuthenticationRequestBuilder add(
      ClientNotificationToken clientNotificationToken) {
    this.clientNotificationToken = clientNotificationToken;
    return this;
  }

  public BackchannelAuthenticationRequestBuilder add(BindingMessage bindingMessage) {
    this.bindingMessage = bindingMessage;
    return this;
  }

  public BackchannelAuthenticationRequestBuilder add(RequestedExpiry requestedExpiry) {
    this.requestedExpiry = requestedExpiry;
    return this;
  }

  public BackchannelAuthenticationRequestBuilder add(AuthorizationDetails authorizationDetails) {
    this.authorizationDetails = authorizationDetails;
    return this;
  }

  public BackchannelAuthenticationRequest build() {
    return new BackchannelAuthenticationRequest(
        identifier,
        tenantIdentifier,
        profile,
        deliveryMode,
        scopes,
        requestedClientId,
        idTokenHint,
        loginHint,
        loginHintToken,
        acrValues,
        userCode,
        clientNotificationToken,
        bindingMessage,
        requestedExpiry,
        requestObject,
        authorizationDetails);
  }
}
