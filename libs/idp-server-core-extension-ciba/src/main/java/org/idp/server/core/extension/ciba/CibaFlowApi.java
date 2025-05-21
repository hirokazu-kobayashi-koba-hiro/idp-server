package org.idp.server.core.extension.ciba;

import java.util.Map;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.core.authentication.AuthenticationInteractionRequest;
import org.idp.server.core.authentication.AuthenticationInteractionRequestResult;
import org.idp.server.core.authentication.AuthenticationInteractionType;
import org.idp.server.core.extension.ciba.handler.io.CibaRequestResponse;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

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
}
