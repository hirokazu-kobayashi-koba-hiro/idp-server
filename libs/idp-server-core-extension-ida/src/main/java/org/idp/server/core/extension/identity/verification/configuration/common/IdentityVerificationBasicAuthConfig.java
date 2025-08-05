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

package org.idp.server.core.extension.identity.verification.configuration.common;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.http.BasicAuth;
import org.idp.server.platform.json.JsonReadable;

public class IdentityVerificationBasicAuthConfig implements JsonReadable {
  String username;
  String password;

  public IdentityVerificationBasicAuthConfig() {}

  public IdentityVerificationBasicAuthConfig(String username, String password) {
    this.username = username;
    this.password = password;
  }

  public String username() {
    return username;
  }

  public String password() {
    return password;
  }

  public boolean exists() {
    return username != null && !username.isEmpty() && password != null && !password.isEmpty();
  }

  public BasicAuth basicAuth() {
    return new BasicAuth(username, password);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("username", username);
    map.put("password", password);
    return map;
  }
}
