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

package org.idp.server.account_linking.handler;

import org.idp.server.account_linking.io.LinkedExternalAccountTokenParameters;
import org.idp.server.account_linking.io.LinkedExternalAccountTokenRequest;
import org.idp.server.core.openid.oauth.clientattestation.ClientAttestationJwt;
import org.idp.server.core.openid.oauth.clientattestation.ClientAttestationPopJwt;
import org.idp.server.core.openid.oauth.clientauthenticator.BackchannelRequestContext;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.type.mtls.ClientCert;
import org.idp.server.core.openid.oauth.type.oauth.ClientAuthenticationType;
import org.idp.server.core.openid.oauth.type.oauth.ClientSecretBasic;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * What the shared client authenticators need to know about a stored token retrieval.
 *
 * <p>Built so the endpoint authenticates clients with exactly the code the token endpoint and
 * introspection use, rather than a second implementation of each method.
 */
public class LinkedExternalAccountTokenRequestContext implements BackchannelRequestContext {

  LinkedExternalAccountTokenRequest request;
  RequestedClientId requestedClientId;
  AuthorizationServerConfiguration authorizationServerConfiguration;
  ClientConfiguration clientConfiguration;

  public LinkedExternalAccountTokenRequestContext(
      LinkedExternalAccountTokenRequest request,
      RequestedClientId requestedClientId,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {
    this.request = request;
    this.requestedClientId = requestedClientId;
    this.authorizationServerConfiguration = authorizationServerConfiguration;
    this.clientConfiguration = clientConfiguration;
  }

  @Override
  public LinkedExternalAccountTokenParameters parameters() {
    return request.parameters();
  }

  @Override
  public ClientSecretBasic clientSecretBasic() {
    return request.clientSecretBasic();
  }

  @Override
  public ClientCert clientCert() {
    return request.clientCert();
  }

  @Override
  public boolean hasClientSecretBasic() {
    return request.clientSecretBasic().exists();
  }

  @Override
  public ClientAttestationJwt clientAttestationJwt() {
    return request.clientAttestationJwt();
  }

  @Override
  public ClientAttestationPopJwt clientAttestationPopJwt() {
    return request.clientAttestationPopJwt();
  }

  @Override
  public Tenant tenant() {
    return request.tenant();
  }

  @Override
  public AuthorizationServerConfiguration serverConfiguration() {
    return authorizationServerConfiguration;
  }

  @Override
  public ClientConfiguration clientConfiguration() {
    return clientConfiguration;
  }

  @Override
  public ClientAuthenticationType clientAuthenticationType() {
    return clientConfiguration.clientAuthenticationType();
  }

  @Override
  public RequestedClientId requestedClientId() {
    return requestedClientId;
  }
}
