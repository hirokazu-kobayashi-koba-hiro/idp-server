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

package org.idp.server.control_plane.organization.access;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.tenant.io.TenantManagementResponse;
import org.idp.server.control_plane.management.tenant.io.TenantManagementStatus;

public class OrganizationAccessControlResult {

  public enum AccessControlStatus {
    SUCCESS,
    FORBIDDEN
  }

  private final AccessControlStatus status;
  private final String reason;

  private OrganizationAccessControlResult(AccessControlStatus status, String reason) {
    this.status = status;
    this.reason = reason;
  }

  public static OrganizationAccessControlResult success() {
    return new OrganizationAccessControlResult(AccessControlStatus.SUCCESS, null);
  }

  public static OrganizationAccessControlResult forbidden(String reason) {
    return new OrganizationAccessControlResult(AccessControlStatus.FORBIDDEN, reason);
  }

  public AccessControlStatus getStatus() {
    return status;
  }

  public String getReason() {
    return reason;
  }

  public boolean isSuccess() {
    return status == AccessControlStatus.SUCCESS;
  }

  public boolean isForbidden() {
    return status == AccessControlStatus.FORBIDDEN;
  }

  public TenantManagementResponse toErrorResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("error", "access_denied");
    response.put("error_description", getReason());

    return new TenantManagementResponse(TenantManagementStatus.FORBIDDEN, response);
  }
}
