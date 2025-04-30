package org.idp.server.core.ciba.user;

import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserRepository;
import org.idp.server.core.tenant.Tenant;

public interface UserHintResolver {

  User resolve(
      Tenant tenant,
      UserHint userHint,
      UserHintRelatedParams userHintRelatedParams,
      UserRepository userRepository);
}
