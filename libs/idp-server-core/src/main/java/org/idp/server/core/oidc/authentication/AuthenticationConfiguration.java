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

package org.idp.server.core.oidc.authentication;

import java.util.Map;

public class AuthenticationConfiguration {
  String id;
  String type;
  Map<String, Object> payload;

  public AuthenticationConfiguration() {}

  public AuthenticationConfiguration(String id, String type, Map<String, Object> payload) {
    this.id = id;
    this.type = type;
    this.payload = payload;
  }

  public String id() {
    return id;
  }

  public AuthenticationConfigurationIdentifier identifier() {
    return new AuthenticationConfigurationIdentifier(id);
  }

  public String type() {
    return type;
  }

  public Map<String, Object> payload() {
    return payload;
  }

  public boolean exists() {
    return id != null && !id.isEmpty();
  }
}
