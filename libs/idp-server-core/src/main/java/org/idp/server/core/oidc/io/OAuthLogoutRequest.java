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

package org.idp.server.core.oidc.io;

import java.util.Map;
import org.idp.server.core.oidc.request.OAuthLogoutParameters;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class OAuthLogoutRequest {
  Tenant tenant;
  Map<String, String[]> params;

  public OAuthLogoutRequest() {
    this.params = Map.of();
  }

  public OAuthLogoutRequest(Tenant tenant, Map<String, String[]> params) {
    this.tenant = tenant;
    this.params = params;
  }

  public Tenant tenant() {
    return tenant;
  }

  public OAuthLogoutParameters toParameters() {
    return new OAuthLogoutParameters(params);
  }
}
