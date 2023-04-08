package org.idp.server.core.oauth.verifier;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.oauth.OAuthRequestContext;

/** OAuthRequestVerifier */
public class OAuthRequestVerifier {

  static List<AuthorizationRequestVerifier> verifiers = new ArrayList<>();

  static {
    verifiers.add(new OAuth2RequestVerifier());
    verifiers.add(new OidcRequestVerifier());
    verifiers.add(new RequestObjectVerifier());
  }

  public void verify(OAuthRequestContext oAuthRequestContext) {
    verifiers.forEach(
        verifier -> {
          if (verifier.shouldNotVerify(oAuthRequestContext)) {
            return;
          }
          verifier.verify(oAuthRequestContext);
        });
  }
}
