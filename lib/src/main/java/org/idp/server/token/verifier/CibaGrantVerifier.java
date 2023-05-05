package org.idp.server.token.verifier;

import org.idp.server.ciba.grant.CibaGrant;
import org.idp.server.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.token.TokenRequestContext;

public class CibaGrantVerifier {

  public CibaGrantVerifier() {}

  public void verify(
      TokenRequestContext context, BackchannelAuthenticationRequest request, CibaGrant cibaGrant) {}
}
