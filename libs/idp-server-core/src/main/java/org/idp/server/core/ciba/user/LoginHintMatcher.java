package org.idp.server.core.ciba.user;

import org.idp.server.core.identity.User;
import org.idp.server.basic.type.extension.Pairs;

public interface LoginHintMatcher {
  boolean matches(String hint);

  Pairs<String, String> extractHints(String hint);

  User resolve(Pairs<String, String> hints);
}
