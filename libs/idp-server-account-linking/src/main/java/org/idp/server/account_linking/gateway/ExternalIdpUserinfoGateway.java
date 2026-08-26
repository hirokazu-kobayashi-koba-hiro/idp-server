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

package org.idp.server.account_linking.gateway;

import org.idp.server.account_linking.exception.ExternalIdpRequestFailedException;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.mapper.UserInfoMapper;
import org.idp.server.federation.sso.oidc.*;

/**
 * Reads the external IdP's userinfo endpoint and maps the response onto a {@link User}.
 *
 * <p>Delegates to the executor the federation module already selects by provider type, so the
 * mapping rules written for login work unchanged for linking.
 */
public class ExternalIdpUserinfoGateway {

  OidcSsoExecutors oidcSsoExecutors;

  public ExternalIdpUserinfoGateway(OidcSsoExecutors oidcSsoExecutors) {
    this.oidcSsoExecutors = oidcSsoExecutors;
  }

  /**
   * @return the mapped user. Whether it carries an external user identifier is up to the mapping
   *     rules; the caller decides whether it needs one, because an OpenID Connect provider will
   *     already have supplied a verified sub.
   * @throws ExternalIdpRequestFailedException if the external IdP answered with an error. Called
   *     only when the provider is configured to be asked, so a failure means a configured step did
   *     not work rather than a provider with nothing to say.
   */
  public User request(OidcSsoConfiguration configuration, String accessToken) {
    OidcSsoExecutor executor = oidcSsoExecutors.get(configuration.ssoProvider());
    UserinfoExecutionResult result =
        executor.requestUserInfo(
            new OidcUserinfoRequest(
                configuration.userinfoEndpoint(), accessToken, configuration.userinfoExecution()));

    if (result.isError()) {
      throw new ExternalIdpRequestFailedException(
          "Account linking failed at the external identity provider userinfo endpoint.");
    }

    return new UserInfoMapper(
            configuration.userinfoMappingRules(), result.contents(), configuration.issuerName())
        .toUser();
  }
}
