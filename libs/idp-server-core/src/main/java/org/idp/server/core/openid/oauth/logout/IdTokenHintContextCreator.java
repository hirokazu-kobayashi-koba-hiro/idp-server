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

package org.idp.server.core.openid.oauth.logout;

import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.request.OAuthLogoutParameters;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * IdTokenHintContextCreator
 *
 * <p>Strategy interface for parsing and validating id_token_hint based on its format (JWS,
 * symmetric JWE, asymmetric JWE). Each implementation is responsible for resolving the client
 * configuration.
 *
 * @see <a href="https://openid.net/specs/openid-connect-rpinitiated-1_0.html">RP-Initiated
 *     Logout</a>
 */
public interface IdTokenHintContextCreator {

  /**
   * Parses id_token_hint and resolves client configuration.
   *
   * @param tenant the tenant
   * @param parameters the logout parameters
   * @param serverConfiguration the authorization server configuration
   * @param clientConfigurationQueryRepository the repository for client lookup
   * @return the result containing parsed claims and client configuration
   */
  IdTokenHintResult create(
      Tenant tenant,
      OAuthLogoutParameters parameters,
      AuthorizationServerConfiguration serverConfiguration,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository);
}
