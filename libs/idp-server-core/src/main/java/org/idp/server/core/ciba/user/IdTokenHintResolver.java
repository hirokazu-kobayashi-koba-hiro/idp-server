package org.idp.server.core.ciba.user;

import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.tenant.Tenant;

public class IdTokenHintResolver implements UserHintResolver {

  @Override
  public User resolve(Tenant tenant, UserHint userHint, UserRepository userRepository) {

    return null;
  }
}
