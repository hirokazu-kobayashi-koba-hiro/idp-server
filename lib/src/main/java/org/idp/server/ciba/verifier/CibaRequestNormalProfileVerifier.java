package org.idp.server.ciba.verifier;

import org.idp.server.ciba.CibaRequestContext;

public class CibaRequestNormalProfileVerifier implements CibaVerifier {

  CibaRequestBaseVerifier baseVerifier;

  public CibaRequestNormalProfileVerifier() {
    this.baseVerifier = new CibaRequestBaseVerifier();
  }

  @Override
  public void verify(CibaRequestContext context) {
    baseVerifier.verify(context);
  }
}
