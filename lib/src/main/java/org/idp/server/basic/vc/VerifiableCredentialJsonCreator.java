package org.idp.server.basic.vc;

import java.util.Map;

public class VerifiableCredentialJsonCreator {

  public VerifiableCredential create(Map<String, Object> credentials)
      throws VcInvalidException, VcInvalidKeyException {

    return new VerifiableCredential(credentials);
  }
}
