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

package org.idp.server.core.openid.federation.sso;

import java.io.Serializable;
import org.idp.server.core.openid.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class SsoState implements Serializable, JsonReadable {
  String sessionId;
  String authorizationRequestId;
  String tenantId;
  String provider;

  public SsoState() {}

  public SsoState(
      String sessionId, String authorizationRequestId, String tenantId, String provider) {
    this.sessionId = sessionId;
    this.authorizationRequestId = authorizationRequestId;
    this.tenantId = tenantId;
    this.provider = provider;
  }

  public SsoSessionIdentifier ssoSessionIdentifier() {
    return new SsoSessionIdentifier(sessionId);
  }

  public TenantIdentifier tenantIdentifier() {
    return new TenantIdentifier(tenantId);
  }

  public AuthorizationRequestIdentifier authorizationRequestIdentifier() {
    return new AuthorizationRequestIdentifier(authorizationRequestId);
  }

  public SsoProvider ssoProvider() {
    return new SsoProvider(provider);
  }
}
