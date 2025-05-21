package org.idp.server.core.ciba.user;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.exception.UnSupportedException;

public class UserHintResolvers {

  Map<UserHintType, UserHintResolver> resolvers;

  public UserHintResolvers() {
    this.resolvers = new HashMap<>();
    resolvers.put(UserHintType.LOGIN_HINT, new LoginHintResolver());
    resolvers.put(UserHintType.ID_TOKEN_HINT, new IdTokenHintResolver());
  }

  public UserHintResolver get(UserHintType type) {
    UserHintResolver resolver = resolvers.get(type);

    if (resolver == null) {
      throw new UnSupportedException("Unsupported user hint type: " + type.name());
    }

    return resolver;
  }
}
