package org.idp.server.core.oauth.verifier;

import java.util.*;
import org.idp.server.core.oauth.AuthorizationProfile;
import org.idp.server.core.oauth.OAuthRequestContext;
import org.idp.server.core.oauth.verifier.base.AuthorizationRequestVerifier;
import org.idp.server.core.oauth.verifier.extension.AuthorizationRequestExtensionVerifier;
import org.idp.server.core.oauth.verifier.extension.PckeVerifier;
import org.idp.server.core.oauth.verifier.extension.RequestObjectVerifier;

/** OAuthRequestVerifier */
public class OAuthRequestVerifier {

  static Map<AuthorizationProfile, AuthorizationRequestVerifier> baseVerifiers = new HashMap<>();
  static List<AuthorizationRequestExtensionVerifier> extensionVerifiers = new ArrayList<>();

  static {
    baseVerifiers.put(AuthorizationProfile.OAUTH2, new OAuth2RequestVerifier());
    baseVerifiers.put(AuthorizationProfile.OIDC, new OidcRequestVerifier());
    extensionVerifiers.add(new PckeVerifier());
    extensionVerifiers.add(new RequestObjectVerifier());
  }

  public void verify(OAuthRequestContext context) {
    AuthorizationRequestVerifier baseRequestVerifier = baseVerifiers.get(context.profile());
    if (Objects.isNull(baseRequestVerifier)) {
      throw new RuntimeException(
          String.format("unsupported profile (%s)", context.profile().name()));
    }
    baseRequestVerifier.verify(context);
    extensionVerifiers.forEach(
        verifier -> {
          if (verifier.shouldNotVerify(context)) {
            return;
          }
          verifier.verify(context);
        });
  }
}
