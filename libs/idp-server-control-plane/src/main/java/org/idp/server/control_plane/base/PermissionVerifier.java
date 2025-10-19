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

package org.idp.server.control_plane.base;

import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.exception.PermissionDeniedException;
import org.idp.server.core.openid.identity.User;

/**
 * Verifies that an operator has required permissions for management operations.
 *
 * <p>Centralizes permission checking logic that was duplicated across EntryService classes. Follows
 * the Handler/Service pattern where common cross-cutting concerns are extracted into shared
 * components.
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * PermissionVerifier verifier = new PermissionVerifier();
 * AdminPermissions required = AdminPermissions.of("user:create", "user:update");
 * verifier.verify(operator, required); // throws PermissionDeniedException if denied
 * }</pre>
 *
 * @see org.idp.server.control_plane.management.exception.PermissionDeniedException
 */
public class PermissionVerifier {

  /**
   * Verifies that the operator has all required permissions.
   *
   * @param operator the user performing the operation
   * @param required the permissions required for the operation
   * @throws PermissionDeniedException if operator lacks required permissions
   */
  public void verify(User operator, AdminPermissions required) {
    if (!required.includesAll(operator.permissionsAsSet())) {
      throw new PermissionDeniedException(required, operator.permissionsAsSet());
    }
  }
}
