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

package org.idp.server.core.openid.oauth.clientauthenticator;

import java.util.Objects;
import org.idp.server.core.openid.oauth.clientauthenticator.exception.ClientSecretBasicUnAuthorizedException;
import org.idp.server.core.openid.oauth.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.core.openid.oauth.clientauthenticator.plugin.ClientAuthenticator;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.type.oauth.ClientAuthenticationType;
import org.idp.server.core.openid.oauth.type.oauth.ClientSecretBasic;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;

public class ClientAuthenticationVerifier {
  ClientAuthenticationType clientAuthenticationType;
  ClientAuthenticator clientAuthenticator;
  AuthorizationServerConfiguration authorizationServerConfiguration;
  ClientSecretBasic clientSecretBasic;
  RequestedClientId requestedClientId;

  public ClientAuthenticationVerifier(
      ClientAuthenticationType clientAuthenticationType,
      ClientAuthenticator clientAuthenticator,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientSecretBasic clientSecretBasic,
      RequestedClientId requestedClientId) {
    this.clientAuthenticationType = clientAuthenticationType;
    this.clientAuthenticator = clientAuthenticator;
    this.authorizationServerConfiguration = authorizationServerConfiguration;
    this.clientSecretBasic = clientSecretBasic;
    this.requestedClientId = requestedClientId;
  }

  public void verify() {
    throwExceptionIfBasicCredentialsForAnotherMethod();
    if (Objects.isNull(clientAuthenticator)) {
      throw new ClientUnAuthorizedException(
          String.format(
              "idp does not supported client authentication type (%s)",
              clientAuthenticationType.name()));
    }
    if (!authorizationServerConfiguration.isSupportedClientAuthenticationType(
        clientAuthenticationType.name())) {
      throw new ClientUnAuthorizedException(
          String.format(
              "server does not supported client authentication type (%s)",
              clientAuthenticationType.name()));
    }
  }

  /**
   * RFC 6749 Section 2.3: "The client MUST NOT use more than one authentication method in each
   * request." Basic credentials sent by a client registered for another method are therefore a
   * failed client authentication, and, as the client attempted to authenticate via the {@code
   * Authorization} header, Section 5.2 has it answered with the Basic challenge.
   */
  void throwExceptionIfBasicCredentialsForAnotherMethod() {
    if (clientSecretBasic.exists() && !clientAuthenticationType.isClientSecretBasic()) {
      throw new ClientSecretBasicUnAuthorizedException(
          clientAuthenticationType.name(),
          requestedClientId,
          "Basic credentials are sent, but the client is not registered for client_secret_basic",
          authorizationServerConfiguration.issuer());
    }
  }
}
