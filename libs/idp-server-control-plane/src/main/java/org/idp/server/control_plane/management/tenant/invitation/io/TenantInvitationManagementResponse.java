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


package org.idp.server.control_plane.management.tenant.invitation.io;

import java.util.Map;

public class TenantInvitationManagementResponse {

  TenantInvitationManagementStatus status;
  Map<String, Object> contents;

  public TenantInvitationManagementResponse(
      TenantInvitationManagementStatus status, Map<String, Object> contents) {
    this.status = status;
    this.contents = contents;
  }

  public TenantInvitationManagementStatus status() {
    return status;
  }

  public int statusCode() {
    return status.statusCode();
  }

  public Map<String, Object> contents() {
    return contents;
  }

  public boolean isOk() {
    return this.status.isOk();
  }
}
