package org.idp.server.core.ciba.verifier;

import org.idp.server.core.ciba.CibaRequestContext;
import org.idp.server.core.ciba.exception.BackchannelAuthenticationBadRequestException;
import org.idp.server.core.oauth.exception.RequestObjectInvalidException;
import org.idp.server.core.oauth.verifier.extension.RequestObjectVerifyable;

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
