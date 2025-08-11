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

package org.idp.server.core.openid.oauth;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.oauth.clientauthenticator.BackchannelRequestContext;
import org.idp.server.core.openid.oauth.clientauthenticator.BackchannelRequestParameters;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.io.OAuthPushedRequestResponse;
import org.idp.server.core.openid.oauth.io.OAuthPushedRequestStatus;
import org.idp.server.core.openid.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.openid.oauth.type.mtls.ClientCert;
import org.idp.server.core.openid.oauth.type.oauth.*;

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
    return oAuthRequestContext.authorizationRequest().requestedClientId();
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
