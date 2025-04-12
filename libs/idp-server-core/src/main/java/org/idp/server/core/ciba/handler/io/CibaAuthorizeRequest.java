package org.idp.server.core.ciba.handler.io;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.ciba.AuthReqId;
import org.idp.server.core.type.extension.CustomProperties;

public class CibaAuthorizeRequest {
  Tenant tenant;
  String authReqId;

  // TODO authentication
  Map<String, Object> customProperties = new HashMap<>();

  public CibaAuthorizeRequest(Tenant tenant, String authReqId) {
    this.tenant = tenant;
    this.authReqId = authReqId;
  }

  public CibaAuthorizeRequest setCustomProperties(Map<String, Object> customProperties) {
    this.customProperties = customProperties;
    return this;
  }

  public AuthReqId toAuthReqId() {
    return new AuthReqId(authReqId);
  }

  public CustomProperties toCustomProperties() {
    return new CustomProperties(customProperties);
  }

  public Tenant tenant() {
    return tenant;
  }

  public TenantIdentifier tenantIdentifier() {
    return tenant.identifier();
  }
}
