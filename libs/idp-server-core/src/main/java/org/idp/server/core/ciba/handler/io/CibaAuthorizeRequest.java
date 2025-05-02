package org.idp.server.core.ciba.handler.io;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.type.extension.CustomProperties;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;

public class CibaAuthorizeRequest {
  Tenant tenant;
  BackchannelAuthenticationRequestIdentifier backchannleAuthenticationIdentifier;
  // TODO authentication
  Map<String, Object> customProperties = new HashMap<>();

  public CibaAuthorizeRequest(
      Tenant tenant,
      BackchannelAuthenticationRequestIdentifier backchannleAuthenticationIdentifier) {
    this.tenant = tenant;
    this.backchannleAuthenticationIdentifier = backchannleAuthenticationIdentifier;
  }

  public CibaAuthorizeRequest setCustomProperties(Map<String, Object> customProperties) {
    this.customProperties = customProperties;
    return this;
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

  public BackchannelAuthenticationRequestIdentifier backchannleAuthenticationIdentifier() {
    return backchannleAuthenticationIdentifier;
  }
}
