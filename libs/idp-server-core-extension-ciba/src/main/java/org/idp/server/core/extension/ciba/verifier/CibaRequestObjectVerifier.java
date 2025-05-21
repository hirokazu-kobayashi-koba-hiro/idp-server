package org.idp.server.core.extension.ciba.verifier;

import org.idp.server.core.extension.ciba.CibaRequestContext;
import org.idp.server.core.extension.ciba.exception.BackchannelAuthenticationBadRequestException;
import org.idp.server.core.oidc.exception.RequestObjectInvalidException;
import org.idp.server.core.oidc.verifier.extension.RequestObjectVerifyable;

public class CibaRequestObjectVerifier implements CibaExtensionVerifier, RequestObjectVerifyable {

  @Override
  public boolean shouldNotVerify(CibaRequestContext context) {
    return !context.isRequestObjectPattern();
  }

  public void verify(CibaRequestContext context) {
    try {
      verify(context.joseContext(), context.serverConfiguration(), context.clientConfiguration());
    } catch (RequestObjectInvalidException exception) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request_object", exception.getMessage());
    }
  }
}
