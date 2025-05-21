package org.idp.server.core.extension.ciba.verifier;

import org.idp.server.core.extension.ciba.CibaRequestContext;

public interface CibaVerifier {

  void verify(CibaRequestContext context);
}
