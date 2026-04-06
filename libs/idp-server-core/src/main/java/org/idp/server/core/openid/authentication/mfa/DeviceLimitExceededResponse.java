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

package org.idp.server.core.openid.authentication.mfa;

import java.util.HashMap;
import java.util.Map;

/**
 * Creates a standardized error response for device limit exceeded scenarios.
 *
 * <p>Used by FIDO2 and FIDO-UAF registration (both challenge and registration stages) to return a
 * consistent error response when the user has reached the maximum number of allowed devices.
 *
 * <p>The error code {@code device_limit_exceeded} allows clients to identify this specific error
 * and display appropriate UI guidance (e.g., "Please remove an existing device before registering a
 * new one").
 */
public class DeviceLimitExceededResponse {

  public static Map<String, Object> create(int maxDevices, int currentDevices) {
    Map<String, Object> errorResponse = new HashMap<>();
    errorResponse.put("error", "device_limit_exceeded");
    errorResponse.put(
        "error_description",
        String.format(
            "Maximum number of devices reached %d, user has already %d devices.",
            maxDevices, currentDevices));
    errorResponse.put("max_devices", maxDevices);
    errorResponse.put("current_devices", currentDevices);
    return errorResponse;
  }
}
