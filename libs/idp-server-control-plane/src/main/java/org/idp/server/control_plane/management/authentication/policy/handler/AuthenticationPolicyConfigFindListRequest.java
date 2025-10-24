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

package org.idp.server.control_plane.management.authentication.policy.handler;

import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigManagementRequest;

/**
 * Request wrapper for findList operation.
 *
 * @param limit maximum number of results to return
 * @param offset offset for pagination
 */
public record AuthenticationPolicyConfigFindListRequest(int limit, int offset)
    implements AuthenticationPolicyConfigManagementRequest {

  @Override
  public java.util.Map<String, Object> toMap() {
    return java.util.Map.of("limit", limit, "offset", offset);
  }
}
