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


package org.idp.server.core.oidc.federation.io;

import java.util.Map;
import org.idp.server.core.oidc.federation.FederationCallbackParameters;
import org.idp.server.core.oidc.federation.sso.SsoProvider;
import org.idp.server.core.oidc.federation.sso.SsoState;
import org.idp.server.core.oidc.federation.sso.SsoStateCoder;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class FederationCallbackRequest {

  Map<String, String[]> params;

  public FederationCallbackRequest() {}

  public FederationCallbackRequest(Map<String, String[]> params) {
    this.params = params;
  }

  public FederationCallbackParameters parameters() {
    return new FederationCallbackParameters(params);
  }

  public String state() {
    if (parameters().hasState()) {
      return parameters().state().value();
    }
    return "";
  }

  public SsoState ssoState() {
    if (parameters().hasState()) {
      return SsoStateCoder.decode(state());
    }
    return new SsoState();
  }

  public TenantIdentifier tenantIdentifier() {
    return ssoState().tenantIdentifier();
  }

  public SsoProvider ssoProvider() {
    if (parameters().hasState()) {
      return ssoState().ssoProvider();
    }
    return new SsoProvider();
  }
}
