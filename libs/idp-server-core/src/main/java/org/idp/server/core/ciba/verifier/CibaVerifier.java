package org.idp.server.core.ciba.verifier;

import org.idp.server.core.ciba.CibaRequestContext;

public interface CibaVerifier {

  void verify(CibaRequestContext context);
}
