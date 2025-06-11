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

package org.idp.server.core.oidc.federation;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.oidc.federation.sso.SsoProvider;

public class FederationConfiguration {
  String id;
  String type;
  String ssoProvider;
  Map<String, Object> payload;

  public FederationConfiguration() {}

  public FederationConfiguration(
      String id, String type, String ssoProvider, Map<String, Object> payload) {
    this.id = id;
    this.type = type;
    this.ssoProvider = ssoProvider;
    this.payload = payload;
  }

  public FederationConfigurationIdentifier identifier() {
    return new FederationConfigurationIdentifier(id);
  }

  public FederationType type() {
    return new FederationType(type);
  }

  public String typeName() {
    return type;
  }

  public SsoProvider ssoProvider() {
    return new SsoProvider(ssoProvider);
  }

  public Map<String, Object> payload() {
    return payload;
  }

  public boolean exists() {
    return id != null && !id.isEmpty();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new HashMap<>();
    result.put("id", id);
    result.put("type", type);
    result.put("payload", payload);
    return result;
  }
}
