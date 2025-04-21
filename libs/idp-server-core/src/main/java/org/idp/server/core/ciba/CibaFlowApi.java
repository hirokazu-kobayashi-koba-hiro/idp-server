package org.idp.server.core.ciba;

import java.util.Map;
import org.idp.server.core.authentication.AuthenticationInteractionRequest;
import org.idp.server.core.authentication.AuthenticationInteractionRequestResult;
import org.idp.server.core.authentication.AuthenticationInteractionType;
import org.idp.server.core.ciba.handler.io.CibaAuthorizeResponse;
import org.idp.server.core.ciba.handler.io.CibaDenyResponse;
import org.idp.server.core.ciba.handler.io.CibaRequestResponse;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.security.RequestAttributes;

public interface CibaFlowApi {
  CibaRequestResponse request(
      TenantIdentifier tenantIdentifier,
      Map<String, String[]> params,
      String authorizationHeader,
      String clientCert,
      RequestAttributes requestAttributes);

  AuthenticationInteractionRequestResult interact(
      TenantIdentifier tenantIdentifier,
      BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes);

  CibaAuthorizeResponse authorize(
      TenantIdentifier tenantIdentifier, String authReqId, RequestAttributes requestAttributes);

  CibaDenyResponse deny(
      TenantIdentifier tenantIdentifier, String authReqId, RequestAttributes requestAttributes);
}
