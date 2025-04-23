package org.idp.server.core.ciba.handler.io;

import org.idp.server.core.ciba.CibaRequestContext;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.tenant.Tenant;

public class CibaIssueRequest {

  Tenant tenant;
  CibaRequestContext cibaRequestContext;
  User user;

  public CibaIssueRequest(Tenant tenant, CibaRequestContext cibaRequestContext, User user) {
    this.tenant = tenant;
    this.cibaRequestContext = cibaRequestContext;
    this.user = user;
  }

  public Tenant tenant() {
    return tenant;
  }

  public CibaRequestContext context() {
    return cibaRequestContext;
  }

  public BackchannelAuthenticationRequest request() {
    return cibaRequestContext.backchannelAuthenticationRequest();
  }

  public User user() {
    return user;
  }
}
