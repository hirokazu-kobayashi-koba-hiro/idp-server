package org.idp.server.ciba.verifier;

import org.idp.server.ciba.CibaRequestContext;

public interface CibaVerifier {

  void verify(CibaRequestContext context);
}
