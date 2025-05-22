/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.control_plane.management.tenant.invitation;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.tenant.invitation.io.TenantInvitationManagementResponse;
import org.idp.server.control_plane.management.tenant.invitation.io.TenantInvitationManagementStatus;
import org.idp.server.control_plane.management.tenant.invitation.operation.TenantInvitation;

public class TenantInvitationContext {

  TenantInvitation tenantInvitation;
  boolean dryRun;

  public TenantInvitationContext() {}

  public TenantInvitationContext(TenantInvitation tenantInvitation, boolean dryRun) {
    this.tenantInvitation = tenantInvitation;
    this.dryRun = dryRun;
  }

  public TenantInvitation tenantInvitation() {
    return tenantInvitation;
  }

  public boolean isDryRun() {
    return dryRun;
  }

  public TenantInvitationManagementResponse toResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("result", tenantInvitation.toMap());
    response.put("dry_run", dryRun);
    return new TenantInvitationManagementResponse(TenantInvitationManagementStatus.OK, response);
  }
}
