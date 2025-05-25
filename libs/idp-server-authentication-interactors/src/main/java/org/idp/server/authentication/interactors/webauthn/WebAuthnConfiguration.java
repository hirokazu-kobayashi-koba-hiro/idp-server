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

package org.idp.server.authentication.interactors.webauthn;

import java.util.Map;
import org.idp.server.platform.json.JsonReadable;

public class WebAuthnConfiguration implements JsonReadable {
  String type;
  Map<String, Map<String, Object>> details;

  public WebAuthnConfiguration() {}

  public WebAuthnExecutorType type() {
    return new WebAuthnExecutorType(type);
  }

  public Map<String, Map<String, Object>> details() {
    return details;
  }

  public Map<String, Object> getDetail(WebAuthnExecutorType key) {
    if (!details.containsKey(key.value())) {
      throw new WebAuthnCredentialNotFoundException(
          "invalid configuration. key: " + key.value() + " is unregistered.");
    }
    return details.get(key.value());
  }
}
