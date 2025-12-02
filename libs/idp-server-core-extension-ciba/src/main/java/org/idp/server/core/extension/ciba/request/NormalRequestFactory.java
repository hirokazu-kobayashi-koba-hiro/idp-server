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

import java.time.LocalDateTime;
import java.util.Set;
import org.idp.server.core.extension.ciba.CibaProfile;
import org.idp.server.core.extension.ciba.CibaRequestParameters;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.type.extension.ExpiresAt;
import org.idp.server.core.openid.oauth.type.oauth.ClientSecretBasic;
import org.idp.server.core.openid.oauth.type.oauth.ExpiresIn;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.jose.JoseContext;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/** NormalRequestFactory */
public class NormalRequestFactory implements BackchannelAuthenticationRequestFactory {

  @Override
  public BackchannelAuthenticationRequest create(
      Tenant tenant,
      CibaProfile profile,
      ClientSecretBasic clientSecretBasic,
      CibaRequestParameters parameters,
      JoseContext joseContext,
      Set<String> filteredScopes,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {

    BackchannelAuthenticationRequestBuilder builder =
        new BackchannelAuthenticationRequestBuilder()
            .add(createIdentifier())
            .add(tenant.identifier())
            .add(profile)
            .add(clientConfiguration.backchannelTokenDeliveryMode())
            .add(new Scopes(filteredScopes))
            .add(parameters.idTokenHint())
            .add(parameters.loginHint())
            .add(parameters.loginHintToken())
            .add(parameters.acrValues())
            .add(parameters.bindingMessage())
            .add(parameters.request())
            .add(parameters.clientNotificationToken())
            .add(parameters.userCode())
            .add(parameters.requestedExpiry());

    LocalDateTime now = SystemDateTime.now();
    if (parameters.hasRequestedExpiry()) {
      int expiresIn = parameters.requestedExpiry().valueAsInt();
      builder.add(new ExpiresIn(expiresIn)).add(new ExpiresAt(now.plusSeconds(expiresIn)));
    } else {
      int expiresIn = authorizationServerConfiguration.backchannelAuthenticationRequestExpiresIn();
      builder.add(new ExpiresIn(expiresIn));
      builder.add(new ExpiresAt(now.plusSeconds(expiresIn)));
    }

    if (parameters.hasAuthorizationDetails()) {
      builder.add(parameters.authorizationDetails());
    }

    builder.add(getClientId(clientSecretBasic, parameters));

    return builder.build();
  }

  /**
   * Extracts the client_id from available sources in priority order.
   *
   * <p>Per RFC 7521 Section 4.2 and RFC 7523 Section 3, client_id can be identified from:
   *
   * <ol>
   *   <li>HTTP request parameters (explicit client_id)
   *   <li>HTTP Basic Authentication
   *   <li>client_assertion JWT's iss claim
   * </ol>
   *
   * @param clientSecretBasic HTTP Basic Authentication credentials
   * @param parameters HTTP request parameters
   * @return the client_id from the highest priority source
   */
  private static RequestedClientId getClientId(
      ClientSecretBasic clientSecretBasic, CibaRequestParameters parameters) {
    // 1. Check HTTP request parameters
    if (parameters.hasClientId()) {
      return parameters.clientId();
    }
    // 2. Check HTTP Basic Authentication
    if (clientSecretBasic.exists()) {
      return clientSecretBasic.clientId();
    }
    // 3. Extract from client_assertion JWT's iss claim (RFC 7521/7523)
    if (parameters.hasClientAssertion()) {
      String issuer = parameters.clientAssertion().extractIssuer();
      if (!issuer.isEmpty()) {
        return new RequestedClientId(issuer);
      }
    }
    return new RequestedClientId();
  }
}
