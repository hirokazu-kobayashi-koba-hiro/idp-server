package org.idp.server.core.federation.sso;

import java.io.Serializable;
import org.idp.server.basic.json.JsonReadable;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;

public class SsoState implements Serializable, JsonReadable {
  String sessionId;
  String tenantId;

  public SsoState() {}

  public SsoState(String sessionId, String tenantId) {
    this.sessionId = sessionId;
    this.tenantId = tenantId;
  }

  public SsoSessionIdentifier ssoSessionIdentifier() {
    return new SsoSessionIdentifier(sessionId);
  }

  public TenantIdentifier tenantIdentifier() {
    return new TenantIdentifier(tenantId);
  }
}
