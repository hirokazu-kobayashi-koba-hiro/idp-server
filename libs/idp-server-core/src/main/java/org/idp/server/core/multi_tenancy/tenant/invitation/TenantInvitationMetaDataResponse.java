package org.idp.server.core.multi_tenancy.tenant.invitation;

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
