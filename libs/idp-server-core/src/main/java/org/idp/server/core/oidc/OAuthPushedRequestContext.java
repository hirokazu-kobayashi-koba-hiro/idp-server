/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.type.mtls.ClientCert;
import org.idp.server.basic.type.oauth.*;
import org.idp.server.core.oidc.clientauthenticator.BackchannelRequestContext;
import org.idp.server.core.oidc.clientauthenticator.BackchannelRequestParameters;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.io.OAuthPushedRequestResponse;
import org.idp.server.core.oidc.io.OAuthPushedRequestStatus;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;

public class OAuthPushedRequestContext implements BackchannelRequestContext {

  OAuthRequestContext oAuthRequestContext;
  ClientSecretBasic clientSecretBasic;
  ClientCert clientCert;
  BackchannelRequestParameters backchannelRequestParameters;

  public OAuthPushedRequestContext(
      OAuthRequestContext oAuthRequestContext,
      ClientSecretBasic clientSecretBasic,
      ClientCert clientCert,
      BackchannelRequestParameters backchannelRequestParameters) {
    this.oAuthRequestContext = oAuthRequestContext;
    this.clientSecretBasic = clientSecretBasic;
    this.clientCert = clientCert;
    this.backchannelRequestParameters = backchannelRequestParameters;
  }

  public OAuthRequestContext oAuthRequestContext() {
    return oAuthRequestContext;
  }

  @Override
  public BackchannelRequestParameters parameters() {
    return backchannelRequestParameters;
  }

  @Override
  public ClientSecretBasic clientSecretBasic() {
    return clientSecretBasic;
  }

  @Override
  public ClientCert clientCert() {
    return clientCert;
  }

  @Override
  public boolean hasClientSecretBasic() {
    return clientSecretBasic.exists();
  }

  @Override
  public AuthorizationServerConfiguration serverConfiguration() {
    return oAuthRequestContext.serverConfiguration();
  }

  @Override
  public ClientConfiguration clientConfiguration() {
    return oAuthRequestContext.clientConfiguration();
  }

  @Override
  public ClientAuthenticationType clientAuthenticationType() {
    return oAuthRequestContext.clientAuthenticationType();
  }

  @Override
  public RequestedClientId requestedClientId() {
    return oAuthRequestContext.authorizationRequest().retrieveClientId();
  }

  public OAuthPushedRequestResponse createResponse() {
    AuthorizationRequestIdentifier authorizationRequestIdentifier =
        oAuthRequestContext.authorizationRequestIdentifier();
    ExpiresIn expiresIn = oAuthRequestContext.expiresIn();

    Map<String, Object> contents = new HashMap<>();
    contents.put(
        "request_uri", RequestUri.createPushedRequestUri(authorizationRequestIdentifier.value()));
    contents.put("expires_in", expiresIn.value());

    return new OAuthPushedRequestResponse(OAuthPushedRequestStatus.OK, contents);
  }
}
