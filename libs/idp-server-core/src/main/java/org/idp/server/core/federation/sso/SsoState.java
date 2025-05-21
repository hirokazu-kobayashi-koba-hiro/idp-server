package org.idp.server.core.federation.sso;

import java.io.Serializable;
import org.idp.server.basic.json.JsonReadable;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class SsoState implements Serializable, JsonReadable {
  String sessionId;
  String tenantId;
  String provider;

  public SsoState() {}

  public SsoState(String sessionId, String tenantId, String provider) {
    this.sessionId = sessionId;
    this.tenantId = tenantId;
    this.provider = provider;
  }

  public SsoSessionIdentifier ssoSessionIdentifier() {
    return new SsoSessionIdentifier(sessionId);
  }

  public TenantIdentifier tenantIdentifier() {
    return new TenantIdentifier(tenantId);
  }

  public SsoProvider ssoProvider() {
    return new SsoProvider(provider);
  }
}
