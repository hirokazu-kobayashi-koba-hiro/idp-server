package org.idp.server.core.ciba.handler.io;

import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.type.ciba.AuthReqId;

public class CibaDenyRequest {
  Tenant tenant;
  String authReqId;

  public CibaDenyRequest(Tenant tenant, String authReqId) {
    this.tenant = tenant;
    this.authReqId = authReqId;
  }

  public AuthReqId toAuthReqId() {
    return new AuthReqId(authReqId);
  }

  public Tenant tenant() {
    return tenant;
  }
}
