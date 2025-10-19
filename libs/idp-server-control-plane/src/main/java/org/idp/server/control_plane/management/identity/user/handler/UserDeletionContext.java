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

package org.idp.server.control_plane.management.identity.user.handler;

import java.util.Map;
import org.idp.server.core.openid.identity.User;

/**
 * Context for user deletion operations.
 *
 * <p>Holds the user being deleted and dry-run flag for audit logging purposes.
 *
 * @param user the user being deleted
 * @param dryRun whether this is a dry-run operation
 */
public record UserDeletionContext(User user, boolean dryRun) {

  /**
   * Returns user data as map for audit logging.
   *
   * @return masked user data map (sensitive fields masked)
   */
  public Map<String, Object> beforePayload() {
    return user.toMaskedValueMap();
  }
}
