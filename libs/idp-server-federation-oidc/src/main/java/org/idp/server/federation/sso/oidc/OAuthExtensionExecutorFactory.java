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

package org.idp.server.federation.sso.oidc;

import org.idp.server.core.openid.federation.sso.SsoProvider;
import org.idp.server.platform.dependency.ApplicationComponentContainer;
import org.idp.server.platform.http.HttpRequestExecutor;
import org.idp.server.platform.oauth.OAuthAuthorizationResolvers;

public class OAuthExtensionExecutorFactory implements OidcSsoExecutorFactory {

  @Override
  public OidcSsoExecutor create(ApplicationComponentContainer container) {
    HttpRequestExecutor httpRequestExecutor = container.resolve(HttpRequestExecutor.class);
    OAuthAuthorizationResolvers oAuthAuthorizationResolvers =
        container.resolve(OAuthAuthorizationResolvers.class);
    UserinfoExecutors userinfoExecutors = new UserinfoExecutors(oAuthAuthorizationResolvers);
    return new OAuthExtensionExecutor(httpRequestExecutor, userinfoExecutors);
  }

  @Override
  public String type() {
    return "oauth-extension";
  }

  @Override
  public SsoProvider ssoProvider() {
    return new SsoProvider("oauth-extension");
  }
}
