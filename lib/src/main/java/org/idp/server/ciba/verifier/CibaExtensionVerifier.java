package org.idp.server.ciba.verifier;

import org.idp.server.ciba.CibaRequestContext;

public interface CibaExtensionVerifier {

  default boolean shouldNotVerify(CibaRequestContext context) {
    return false;
  }

  void verify(CibaRequestContext context);
}
