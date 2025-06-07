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

package org.idp.server.core.extension.ciba.handler.io;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.type.extension.CustomProperties;
import org.idp.server.basic.type.extension.DeniedScopes;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.oidc.authentication.Authentication;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class CibaAuthorizeRequest {
  Tenant tenant;
  BackchannelAuthenticationRequestIdentifier backchannleAuthenticationIdentifier;
  Authentication authentication;
  Map<String, Object> customProperties = new HashMap<>();
  List<String> deniedScopes = new ArrayList<>();

  public CibaAuthorizeRequest(
      Tenant tenant,
      BackchannelAuthenticationRequestIdentifier backchannleAuthenticationIdentifier,
      Authentication authentication) {
    this.tenant = tenant;
    this.backchannleAuthenticationIdentifier = backchannleAuthenticationIdentifier;
    this.authentication = authentication;
  }

  public CibaAuthorizeRequest setCustomProperties(Map<String, Object> customProperties) {
    this.customProperties = customProperties;
    return this;
  }

  public CibaAuthorizeRequest setDeniedScopes(List<String> deniedScopes) {
    this.deniedScopes = deniedScopes;
    return this;
  }

  public CustomProperties toCustomProperties() {
    return new CustomProperties(customProperties);
  }

  public Tenant tenant() {
    return tenant;
  }

  public TenantIdentifier tenantIdentifier() {
    return tenant.identifier();
  }

  public BackchannelAuthenticationRequestIdentifier backchannleAuthenticationIdentifier() {
    return backchannleAuthenticationIdentifier;
  }

  public Authentication authentication() {
    return authentication;
  }

  public DeniedScopes toDeniedScopes() {
    return new DeniedScopes(deniedScopes);
  }
}
