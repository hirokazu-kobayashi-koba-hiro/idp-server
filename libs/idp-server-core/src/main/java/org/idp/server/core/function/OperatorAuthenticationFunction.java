package org.idp.server.core.function;

import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.type.extension.Pairs;

public interface OperatorAuthenticationFunction {

  Pairs<User, String> authenticate(String authorizationHeader);
}
