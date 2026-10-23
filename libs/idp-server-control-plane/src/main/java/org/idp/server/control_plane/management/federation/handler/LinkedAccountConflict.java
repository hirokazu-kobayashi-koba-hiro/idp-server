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

package org.idp.server.control_plane.management.federation.handler;

import java.util.Map;
import org.idp.server.control_plane.management.federation.io.FederationConfigManagementResponse;
import org.idp.server.control_plane.management.federation.io.FederationConfigManagementStatus;

/** The answer when linked external accounts still depend on the configuration being changed. */
class LinkedAccountConflict {

  static FederationConfigManagementResponse response(long links) {
    return new FederationConfigManagementResponse(
        FederationConfigManagementStatus.CONFLICT,
        Map.of(
            "error",
            "conflict",
            "error_description",
            "Linked external accounts still use this provider. Remove them first.",
            "linked_external_accounts",
            links));
  }
}
