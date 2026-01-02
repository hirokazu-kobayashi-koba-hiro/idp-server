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

import java.util.Map;
import org.idp.server.core.openid.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.openid.session.OPSession;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class OAuthViewDataRequest {
  Tenant tenant;
  String id;
  OPSession opSession;
  Map<String, Object> additionalViewData;

  public OAuthViewDataRequest(Tenant tenant, String id) {
    this.tenant = tenant;
    this.id = id;
  }

  public OAuthViewDataRequest(Tenant tenant, String id, Map<String, Object> additionalViewData) {
    this.tenant = tenant;
    this.id = id;
    this.additionalViewData = additionalViewData;
  }

  public OAuthViewDataRequest(
      Tenant tenant, String id, OPSession opSession, Map<String, Object> additionalViewData) {
    this.tenant = tenant;
    this.id = id;
    this.opSession = opSession;
    this.additionalViewData = additionalViewData;
  }

  public Tenant tenant() {
    return tenant;
  }

  public AuthorizationRequestIdentifier toIdentifier() {
    return new AuthorizationRequestIdentifier(id);
  }

  public OPSession opSession() {
    return opSession;
  }

  public Map<String, Object> additionalViewData() {
    return additionalViewData;
  }
}
