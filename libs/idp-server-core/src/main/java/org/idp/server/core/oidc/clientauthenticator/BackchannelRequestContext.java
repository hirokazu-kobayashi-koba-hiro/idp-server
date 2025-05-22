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


package org.idp.server.core.oidc.clientauthenticator;

import org.idp.server.basic.type.mtls.ClientCert;
import org.idp.server.basic.type.oauth.ClientAuthenticationType;
import org.idp.server.basic.type.oauth.ClientSecretBasic;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;

public interface BackchannelRequestContext {

  BackchannelRequestParameters parameters();

  ClientSecretBasic clientSecretBasic();

  ClientCert clientCert();

  boolean hasClientSecretBasic();

  AuthorizationServerConfiguration serverConfiguration();

  ClientConfiguration clientConfiguration();

  ClientAuthenticationType clientAuthenticationType();

  RequestedClientId requestedClientId();
}
