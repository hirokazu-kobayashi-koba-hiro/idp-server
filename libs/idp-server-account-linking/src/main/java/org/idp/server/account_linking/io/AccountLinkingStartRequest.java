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

package org.idp.server.account_linking.io;

import java.util.Map;

/** Body of the link start call. */
public class AccountLinkingStartRequest {

  Map<String, Object> values;

  public AccountLinkingStartRequest(Map<String, Object> values) {
    this.values = values == null ? Map.of() : values;
  }

  public String redirectUri() {
    return optString("redirect_uri");
  }

  public String scope() {
    return optString("scope");
  }

  private String optString(String key) {
    Object value = values.get(key);
    return value == null ? null : value.toString();
  }
}
