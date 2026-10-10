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

import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.openid.oauth.clientauthenticator.exception.ClientSecretBasicUnAuthorizedException;
import org.idp.server.core.openid.oauth.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.core.openid.oauth.clientauthenticator.plugin.ClientAuthenticator;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.configuration.exception.ClientConfigurationNotFoundException;
import org.idp.server.core.openid.oauth.type.oauth.ClientAuthenticationType;
import org.idp.server.core.openid.oauth.type.oauth.ClientSecretBasic;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class ClientAuthenticationHandler {

  ClientAuthenticators authenticators;

  public ClientAuthenticationHandler(ClientAuthenticators authenticators) {
    this.authenticators = authenticators;
  }

  /**
   * Looks up the client that a back-channel request names, before it is authenticated.
   *
   * <p>An unknown client is a failed client authentication, not a missing resource: RFC 6749
   * Section 5.2 lists "unknown client" under {@code invalid_client}, as does CIBA Core Section 13.
   * A client that sent Basic credentials in the {@code Authorization} header is answered with the
   * Basic challenge that Section 5.2 requires.
   */
  public ClientConfiguration findClient(
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      Tenant tenant,
      RequestedClientId requestedClientId,
      ClientSecretBasic clientSecretBasic,
      AuthorizationServerConfiguration serverConfiguration) {
    try {
      return clientConfigurationQueryRepository.get(tenant, requestedClientId);
    } catch (ClientConfigurationNotFoundException exception) {
      if (clientSecretBasic.exists()) {
        throw new ClientSecretBasicUnAuthorizedException(
            ClientAuthenticationType.client_secret_basic.name(),
            requestedClientId,
            "client is not found",
            serverConfiguration.issuer());
      }
      throw new ClientUnAuthorizedException("unknown", requestedClientId, "client is not found");
    }
  }

  public ClientCredentials authenticate(BackchannelRequestContext context) {
    ClientAuthenticator clientAuthenticator =
        authenticators.get(context.clientAuthenticationType());

    ClientAuthenticationVerifier verifier =
        new ClientAuthenticationVerifier(
            context.clientAuthenticationType(),
            clientAuthenticator,
            context.serverConfiguration(),
            context.clientSecretBasic(),
            context.requestedClientId());
    verifier.verify();
    return clientAuthenticator.authenticate(context);
  }
}
