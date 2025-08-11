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

package org.idp.server.core.openid.oauth.io;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.authentication.Authentication;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.openid.oauth.type.extension.CustomProperties;
import org.idp.server.core.openid.oauth.type.extension.DeniedScopes;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

/** OAuthAuthorizeRequest */
public class OAuthAuthorizeRequest {
  Tenant tenant;
  String id;
  User user;
  Authentication authentication;
  List<String> deniedScopes = new ArrayList<>();
  Map<String, Object> customProperties = new HashMap<>();

  public OAuthAuthorizeRequest(Tenant tenant, String id, User user, Authentication authentication) {
    this.tenant = tenant;
    this.id = id;
    this.user = user;
    this.authentication = authentication;
  }

  public OAuthAuthorizeRequest setDeniedScopes(List<String> deniedScopes) {
    this.deniedScopes = deniedScopes;
    return this;
  }

  public OAuthAuthorizeRequest setCustomProperties(Map<String, Object> customProperties) {
    this.customProperties = customProperties;
    return this;
  }

  public Tenant tenant() {
    return tenant;
  }

  public TenantIdentifier tenantIdentifier() {
    return tenant.identifier();
  }

  public AuthorizationRequestIdentifier toIdentifier() {
    return new AuthorizationRequestIdentifier(id);
  }

  public User user() {
    return user;
  }

  public Authentication authentication() {
    return authentication;
  }

  public CustomProperties toCustomProperties() {
    return new CustomProperties(customProperties);
  }

  public DeniedScopes toDeniedScopes() {
    return new DeniedScopes(deniedScopes);
  }
}
