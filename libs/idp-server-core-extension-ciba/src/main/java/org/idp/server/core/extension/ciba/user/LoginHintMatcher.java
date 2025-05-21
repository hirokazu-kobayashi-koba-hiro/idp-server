package org.idp.server.core.extension.ciba.user;

import org.idp.server.basic.type.extension.Pairs;
import org.idp.server.core.oidc.identity.User;

public interface LoginHintMatcher {
  boolean matches(String hint);

  Pairs<String, String> extractHints(String hint);

  User resolve(Pairs<String, String> hints);
}
