package org.idp.server.ciba.verifier;

import org.idp.server.ciba.CibaRequestContext;

public class RequestObjectVerifier implements CibaExtensionVerifier, RequestObjectVerifyable {

  @Override
  public boolean shouldNotVerify(CibaRequestContext context) {
    return !context.isRequestObjectPattern();
  }

  public void verify(CibaRequestContext context) {
    verify(context.joseContext(), context);
  }
}
