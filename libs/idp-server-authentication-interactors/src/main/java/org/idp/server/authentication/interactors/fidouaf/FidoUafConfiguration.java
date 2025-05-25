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

package org.idp.server.authentication.interactors.fidouaf;

import java.util.Map;
import org.idp.server.authentication.interactors.webauthn.WebAuthnCredentialNotFoundException;
import org.idp.server.platform.json.JsonReadable;

public class FidoUafConfiguration implements JsonReadable {
  String type;
  String deviceIdParam;
  Map<String, Map<String, Object>> details;

  public FidoUafConfiguration() {}

  public FidoUafExecutorType type() {
    return new FidoUafExecutorType(type);
  }

  public String deviceIdParam() {
    return deviceIdParam;
  }

  public Map<String, Map<String, Object>> details() {
    return details;
  }

  public Map<String, Object> getDetail(FidoUafExecutorType type) {
    if (!details.containsKey(type.name())) {
      throw new WebAuthnCredentialNotFoundException(
          "invalid configuration. key: " + type.name() + " is unregistered.");
    }
    return details.get(type.name());
  }
}
