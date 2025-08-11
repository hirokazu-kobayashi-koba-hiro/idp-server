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

package org.idp.server.core.openid.oauth.configuration.authentication;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.json.JsonReadable;

public class AuthenticationDeviceRule implements JsonReadable {
  int maxDevices = 1;
  boolean requiredIdentityVerification = false;

  public AuthenticationDeviceRule() {}

  public int maxDevices() {
    return maxDevices;
  }

  public boolean requiredIdentityVerification() {
    return requiredIdentityVerification;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("max_devices", maxDevices);
    map.put("required_identity_verification", requiredIdentityVerification);
    return map;
  }
}
