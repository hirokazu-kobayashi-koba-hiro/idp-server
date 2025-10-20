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

package org.idp.server.control_plane.management.role.verifier;

import org.idp.server.control_plane.management.role.RoleRegistrationContext;
import org.idp.server.control_plane.management.role.RoleUpdateContext;

public class RoleRegistrationVerifier {

  public RoleRegistrationVerifier() {}

  public void verify(RoleRegistrationContext context) {
    RolePermissionVerifier rolePermissionVerifier =
        new RolePermissionVerifier(context.request(), context.roles(), context.permissions(), null);
    rolePermissionVerifier.verify();
  }

  public void verify(RoleUpdateContext context) {
    RolePermissionVerifier rolePermissionVerifier =
        new RolePermissionVerifier(
            context.request(), context.roles(), context.permissions(), context.before().id());
    rolePermissionVerifier.verify();
  }
}
