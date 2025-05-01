package org.idp.server.core.oidc.verifier;

import java.util.*;
import org.idp.server.core.oidc.AuthorizationProfile;
import org.idp.server.core.oidc.OAuthRequestContext;
import org.idp.server.core.oidc.verifier.base.AuthorizationRequestVerifier;
import org.idp.server.core.oidc.verifier.extension.*;
import org.idp.server.basic.exception.UnSupportedException;

/** OAuthRequestVerifier */
public class OAuthRequestVerifier {

  static Map<AuthorizationProfile, AuthorizationRequestVerifier> baseVerifiers = new HashMap<>();
  static List<AuthorizationRequestExtensionVerifier> extensionVerifiers = new ArrayList<>();

  static {
    baseVerifiers.put(AuthorizationProfile.OAUTH2, new OAuth2RequestVerifier());
    baseVerifiers.put(AuthorizationProfile.OIDC, new OidcRequestVerifier());
    baseVerifiers.put(AuthorizationProfile.FAPI_BASELINE, new FapiBaselineVerifier());
    baseVerifiers.put(AuthorizationProfile.FAPI_ADVANCE, new FapiAdvanceVerifier());
    extensionVerifiers.add(new PckeVerifier());
    extensionVerifiers.add(new RequestObjectVerifier());
    extensionVerifiers.add(new OAuthAuthorizationDetailsVerifier());
    extensionVerifiers.add(new JarmVerifier());
    extensionVerifiers.add(new OAuthVerifiableCredentialVerifier());
  }

  public void verify(OAuthRequestContext context) {
    AuthorizationRequestVerifier baseRequestVerifier = baseVerifiers.get(context.profile());
    if (Objects.isNull(baseRequestVerifier)) {
      throw new UnSupportedException(
          String.format("idp server unsupported profile (%s)", context.profile().name()));
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
