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

package org.idp.server.core.openid.clientinstance.registration;

import java.util.Map;

/** Response of the Client Instance registration endpoints. */
public record ClientInstanceRegistrationResponse(int statusCode, Map<String, Object> contents) {

  public static ClientInstanceRegistrationResponse ok(Map<String, Object> contents) {
    return new ClientInstanceRegistrationResponse(200, contents);
  }

  public static ClientInstanceRegistrationResponse created(Map<String, Object> contents) {
    return new ClientInstanceRegistrationResponse(201, contents);
  }

  /**
   * Registration failures are reported without distinguishing the cause.
   *
   * <p>The endpoint is unauthenticated, so a detailed reason would let a caller probe which
   * client_id / device_id combinations exist or already hold an instance.
   */
  public static ClientInstanceRegistrationResponse invalidRequest() {
    return new ClientInstanceRegistrationResponse(400, Map.of("error", "invalid_request"));
  }
}
