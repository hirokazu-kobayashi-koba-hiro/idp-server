package org.idp.server.adapters.springboot.domain.model.operation;

import java.util.List;
import org.idp.server.core.oauth.identity.User;
import org.springframework.security.authentication.AbstractAuthenticationToken;

public class Operator extends AbstractAuthenticationToken {

  User user;
  String token;

  public Operator(User user, String token, List<IdPScope> idPScopes) {
    super(idPScopes);
    this.user = user;
    this.token = token;
  }

  @Override
  public Object getCredentials() {
    return token;
  }

  @Override
  public Object getPrincipal() {
    return user;
  }
}
