package org.idp.server.core.oidc.verifier;

import java.util.*;
import org.idp.server.core.oidc.AuthorizationProfile;
import org.idp.server.core.oidc.OAuthRequestContext;
import org.idp.server.core.oidc.plugin.*;
import org.idp.server.core.oidc.verifier.extension.JarmVerifier;
import org.idp.server.core.oidc.verifier.extension.OAuthAuthorizationDetailsVerifier;
import org.idp.server.core.oidc.verifier.extension.OAuthVerifiableCredentialVerifier;
import org.idp.server.core.oidc.verifier.extension.RequestObjectVerifier;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.log.LoggerWrapper;

/** OAuthRequestVerifier */
public class OAuthRequestVerifier {

  Map<AuthorizationProfile, AuthorizationRequestVerifier> baseVerifiers = new HashMap<>();
  List<AuthorizationRequestExtensionVerifier> extensionVerifiers = new ArrayList<>();
  LoggerWrapper log = LoggerWrapper.getLogger(OAuthRequestVerifier.class);

  public OAuthRequestVerifier() {
    baseVerifiers.put(AuthorizationProfile.OAUTH2, new OAuth2RequestVerifier());
    baseVerifiers.put(AuthorizationProfile.OIDC, new OidcRequestVerifier());
    Map<AuthorizationProfile, AuthorizationRequestVerifier> loadedVerifiers =
        AuthorizationRequestVerifierLoader.load();
    baseVerifiers.putAll(loadedVerifiers);

    List<AuthorizationRequestExtensionVerifier> loadedExtensionVerifiers =
        AuthorizationRequestExtensionVerifierLoader.load();
    extensionVerifiers.addAll(loadedExtensionVerifiers);
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
