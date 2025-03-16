package org.idp.server.core.basic.vc;

import java.util.Map;

public class VerifiableCredentialJsonCreator {

  public Credential create(Map<String, Object> credentials)
      throws VcInvalidException, VcInvalidKeyException {

    return new Credential(credentials);
  }
}
