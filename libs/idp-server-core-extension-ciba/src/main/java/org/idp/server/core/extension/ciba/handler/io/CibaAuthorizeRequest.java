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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.type.extension.CustomProperties;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class CibaAuthorizeRequest {
  Tenant tenant;
  BackchannelAuthenticationRequestIdentifier backchannleAuthenticationIdentifier;
  // TODO authentication
  Map<String, Object> customProperties = new HashMap<>();

  public CibaAuthorizeRequest(
      Tenant tenant,
      BackchannelAuthenticationRequestIdentifier backchannleAuthenticationIdentifier) {
    this.tenant = tenant;
    this.backchannleAuthenticationIdentifier = backchannleAuthenticationIdentifier;
  }

  public CibaAuthorizeRequest setCustomProperties(Map<String, Object> customProperties) {
    this.customProperties = customProperties;
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
}
