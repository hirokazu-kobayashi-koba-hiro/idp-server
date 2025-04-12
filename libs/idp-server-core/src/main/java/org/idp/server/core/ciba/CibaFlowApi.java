package org.idp.server.core.ciba;

import java.util.Map;
import org.idp.server.core.ciba.handler.io.CibaAuthorizeResponse;
import org.idp.server.core.ciba.handler.io.CibaDenyResponse;
import org.idp.server.core.ciba.handler.io.CibaRequestResponse;
import org.idp.server.core.tenant.TenantIdentifier;

public interface CibaFlowApi {
  CibaRequestResponse request(
      TenantIdentifier tenantIdentifier,
      Map<String, String[]> params,
      String authorizationHeader,
      String clientCert);

  CibaAuthorizeResponse authorize(TenantIdentifier tenantIdentifier, String authReqId);

  CibaDenyResponse deny(TenantIdentifier tenantIdentifier, String authReqId);
}
