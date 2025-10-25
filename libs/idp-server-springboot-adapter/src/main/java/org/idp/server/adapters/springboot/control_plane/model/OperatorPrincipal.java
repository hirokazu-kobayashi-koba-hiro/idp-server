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

package org.idp.server.adapters.springboot.control_plane.model;

import java.util.List;
import org.idp.server.control_plane.base.AdminAuthenticationContext;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.springframework.security.authentication.AbstractAuthenticationToken;

public class OperatorPrincipal extends AbstractAuthenticationToken {

  AdminAuthenticationContext authenticationContext;

  public OperatorPrincipal(
      AdminAuthenticationContext authenticationContext,
      List<IdpControlPlaneAuthority> idpControlPlaneAuthorities) {
    super(idpControlPlaneAuthorities);
    this.authenticationContext = authenticationContext;
  }

  @Override
  public Object getCredentials() {
    return authenticationContext.oAuthToken();
  }

  @Override
  public Object getPrincipal() {
    return this;
  }

  public User getUser() {
    return authenticationContext.operator();
  }

  public OAuthToken getOAuthToken() {
    return authenticationContext.oAuthToken();
  }

  public AdminAuthenticationContext authenticationContext() {
    return authenticationContext;
  }
}
