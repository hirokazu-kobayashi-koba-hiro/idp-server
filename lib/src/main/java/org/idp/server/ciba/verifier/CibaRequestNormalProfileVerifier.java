package org.idp.server.ciba.verifier;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.ciba.CibaRequestContext;

public class CibaRequestNormalProfileVerifier implements CibaVerifier {

  CibaRequestBaseVerifier baseVerifier;
  List<CibaExtensionVerifier> extensionVerifiers;

  public CibaRequestNormalProfileVerifier() {
    this.baseVerifier = new CibaRequestBaseVerifier();
    this.extensionVerifiers = new ArrayList<>();
    extensionVerifiers.add(new CibaRequestObjectVerifier());
  }

  @Override
  public void verify(CibaRequestContext context) {
    baseVerifier.verify(context);
    extensionVerifiers.forEach(
        cibaExtensionVerifier -> {
          if (cibaExtensionVerifier.shouldNotVerify(context)) {
            return;
          }
          cibaExtensionVerifier.verify(context);
        });
  }
}
