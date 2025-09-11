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

import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.multi_tenancy.organization.AssignedTenant;

public class OrganizationAccessControlResult {

  public enum AccessControlStatus {
    SUCCESS,
    FORBIDDEN,
    NOT_FOUND
  }

  private final AccessControlStatus status;
  private final String reason;
  private final User operator;
  private final AssignedTenant assignment;

  private OrganizationAccessControlResult(
      AccessControlStatus status, String reason, User operator, AssignedTenant assignment) {
    this.status = status;
    this.reason = reason;
    this.operator = operator;
    this.assignment = assignment;
  }

  public static OrganizationAccessControlResult success(User operator, AssignedTenant assignment) {
    return new OrganizationAccessControlResult(
        AccessControlStatus.SUCCESS, null, operator, assignment);
  }

  public static OrganizationAccessControlResult forbidden(String reason) {
    return new OrganizationAccessControlResult(AccessControlStatus.FORBIDDEN, reason, null, null);
  }

  public static OrganizationAccessControlResult notFound(String reason) {
    return new OrganizationAccessControlResult(AccessControlStatus.NOT_FOUND, reason, null, null);
  }

  public AccessControlStatus getStatus() {
    return status;
  }

  public String getReason() {
    return reason;
  }

  public User getOperator() {
    return operator;
  }

  public AssignedTenant getAssignment() {
    return assignment;
  }

  public boolean isSuccess() {
    return status == AccessControlStatus.SUCCESS;
  }

  public boolean isForbidden() {
    return status == AccessControlStatus.FORBIDDEN;
  }

  public boolean isNotFound() {
    return status == AccessControlStatus.NOT_FOUND;
  }
}
