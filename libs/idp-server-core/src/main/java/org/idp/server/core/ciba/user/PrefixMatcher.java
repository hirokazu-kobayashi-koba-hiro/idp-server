package org.idp.server.core.ciba.user;

import java.util.function.Function;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.type.extension.Pairs;

public class PrefixMatcher implements LoginHintMatcher {

  String prefix;
  Function<Pairs<String, String>, User> resolver;

  PrefixMatcher(String prefix, Function<Pairs<String, String>, User> resolver) {
    this.prefix = prefix;
    this.resolver = resolver;
  }

  public boolean matches(String hint) {
    return hint.startsWith(prefix);
  }

  public Pairs<String, String> extractHints(String hint) {
    String[] hints = hint.substring(prefix.length()).split(",");
    String userHint = hints.length > 0 ? hints[0] : "";
    String providerHint = hints.length > 1 ? hints[1] : "";
    return Pairs.of(userHint, providerHint);
  }

  public User resolve(Pairs<String, String> hints) {
    return resolver.apply(hints);
  }
}
