package org.idp.server.core.ciba.verifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.ciba.CibaProfile;
import org.idp.server.core.ciba.CibaRequestContext;

public class CibaRequestVerifier {

  Map<CibaProfile, CibaVerifier> baseVerifiers;
  CibaRequestContext context;

  public CibaRequestVerifier(CibaRequestContext context) {
    this.baseVerifiers = new HashMap<>();
    this.baseVerifiers.put(CibaProfile.CIBA, new CibaRequestNormalProfileVerifier());
    this.baseVerifiers.put(CibaProfile.FAPI_CIBA, new CibaRequestFapiProfileVerifier());
    this.context = context;
  }

  public void verify() {
    CibaVerifier cibaVerifier = baseVerifiers.get(context.profile());
    if (Objects.isNull(cibaVerifier)) {
      throw new RuntimeException("unsupported ciba profile");
    }
    cibaVerifier.verify(context);
  }
}
