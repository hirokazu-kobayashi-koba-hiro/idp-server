package org.idp.server.core.discovery;

import org.idp.server.core.discovery.handler.io.JwksRequestResponse;
import org.idp.server.core.tenant.TenantIdentifier;

public interface JwksProtocol {

  JwksRequestResponse getJwks(TenantIdentifier tenantIdentifier);
}
