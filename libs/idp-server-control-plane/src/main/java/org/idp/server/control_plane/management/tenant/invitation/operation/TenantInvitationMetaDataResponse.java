/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.control_plane.management.tenant.invitation.operation;

import java.util.Map;

public class TenantInvitationMetaDataResponse {
  TenantInvitationMetaDataStatus status;
  Map<String, Object> contents;

  public TenantInvitationMetaDataResponse() {}

  public TenantInvitationMetaDataResponse(
      TenantInvitationMetaDataStatus status, Map<String, Object> contents) {
    this.status = status;
    this.contents = contents;
  }

  public TenantInvitationMetaDataStatus status() {
    return status;
  }

  public int statusCode() {
    return status.statusCode();
  }

  public Map<String, Object> contents() {
    return contents;
  }
}
