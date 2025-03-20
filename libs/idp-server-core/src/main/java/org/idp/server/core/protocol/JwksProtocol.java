package org.idp.server.core.protocol;

import org.idp.server.core.handler.discovery.io.JwksRequestResponse;
import org.idp.server.core.tenant.TenantIdentifier;

public interface JwksProtocol {

  JwksRequestResponse getJwks(TenantIdentifier tenantIdentifier);
}
