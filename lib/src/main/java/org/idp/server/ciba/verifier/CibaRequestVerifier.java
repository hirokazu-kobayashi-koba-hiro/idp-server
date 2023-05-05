package org.idp.server.ciba.verifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.ciba.CibaProfile;
import org.idp.server.ciba.CibaRequestContext;

public class CibaRequestVerifier {

  Map<CibaProfile, CibaVerifier> baseVerifiers;

  public CibaRequestVerifier() {
    this.baseVerifiers = new HashMap<>();
    this.baseVerifiers.put(CibaProfile.CIBA, new CibaRequestNormalProfileVerifier());
    this.baseVerifiers.put(CibaProfile.FAPI_CIBA, new CibaRequestFapiProfileVerifier());
  }

  public void verify(CibaRequestContext context) {
    CibaVerifier cibaVerifier = baseVerifiers.get(context.profile());
    if (Objects.isNull(cibaVerifier)) {
      throw new RuntimeException("unsupported ciba profile");
    }
    cibaVerifier.verify(context);
  }
}
