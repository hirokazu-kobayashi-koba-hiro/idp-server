package org.idp.server.core.ciba.user;

import java.util.List;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.type.extension.Pairs;

public class LoginHintResolver implements UserHintResolver {

  @Override
  public User resolve(Tenant tenant, UserHint userHint, UserRepository userRepository) {
    String loginHint = userHint.value();

    List<LoginHintMatcher> matchers =
        List.of(
            new PrefixMatcher("sub:", hints -> userRepository.get(tenant, hints.getLeft())),
            new PrefixMatcher(
                "phone:",
                hints -> userRepository.findByPhone(tenant, hints.getLeft(), hints.getRight())),
            new PrefixMatcher(
                "email:",
                hints -> userRepository.findByEmail(tenant, hints.getLeft(), hints.getRight())));

    return matchers.stream()
        .filter(matcher -> matcher.matches(loginHint))
        .findFirst()
        .map(
            matcher -> {
              Pairs<String, String> hints = matcher.extractHints(loginHint);
              return matcher.resolve(hints);
            })
        .orElse(User.notFound());
  }
}
