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

package org.idp.server.core.oidc.context;

import java.util.Set;
import org.idp.server.core.oidc.AuthorizationProfile;
import org.idp.server.core.oidc.OAuthRequestContext;
import org.idp.server.core.oidc.OAuthRequestPattern;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.factory.NormalRequestFactory;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.request.OAuthRequestParameters;
import org.idp.server.platform.jose.JoseContext;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/** NormalPatternContextService */
public class NormalPatternContextCreator implements OAuthRequestContextCreator {

  NormalRequestFactory normalRequestFactory = new NormalRequestFactory();

  @Override
  public OAuthRequestContext create(
      Tenant tenant,
      OAuthRequestParameters parameters,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {

    JoseContext joseContext = new JoseContext();
    OAuthRequestPattern pattern = OAuthRequestPattern.NORMAL;
    Set<String> filteredScopes =
        filterScopes(pattern, parameters, joseContext, clientConfiguration);
    AuthorizationProfile profile = analyze(filteredScopes, authorizationServerConfiguration);

    AuthorizationRequest authorizationRequest =
        normalRequestFactory.create(
            tenant,
            profile,
            parameters,
            joseContext,
            filteredScopes,
            authorizationServerConfiguration,
            clientConfiguration);

    return new OAuthRequestContext(
        tenant,
        pattern,
        parameters,
        joseContext,
        authorizationRequest,
        authorizationServerConfiguration,
        clientConfiguration);
  }
}
