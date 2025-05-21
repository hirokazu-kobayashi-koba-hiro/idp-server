package org.idp.server.core.extension.ciba.verifier;

import org.idp.server.core.extension.ciba.CibaRequestContext;

public interface CibaExtensionVerifier {

  default boolean shouldNotVerify(CibaRequestContext context) {
    return false;
  }

  void verify(CibaRequestContext context);
}
