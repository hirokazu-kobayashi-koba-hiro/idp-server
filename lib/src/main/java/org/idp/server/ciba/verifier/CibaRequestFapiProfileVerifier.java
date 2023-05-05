package org.idp.server.ciba.verifier;

import org.idp.server.ciba.CibaRequestContext;

public class CibaRequestFapiProfileVerifier implements CibaVerifier {

  CibaRequestBaseVerifier baseVerifier;

  public CibaRequestFapiProfileVerifier() {
    this.baseVerifier = new CibaRequestBaseVerifier();
  }

  @Override
  public void verify(CibaRequestContext context) {
    baseVerifier.verify(context);
  }
}
