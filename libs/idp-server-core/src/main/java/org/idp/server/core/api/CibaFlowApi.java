package org.idp.server.core.api;

import java.util.Map;
import org.idp.server.core.handler.ciba.io.*;
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
