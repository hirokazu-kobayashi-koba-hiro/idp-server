/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.ciba.handler.io;

import org.idp.server.core.extension.ciba.CibaRequestContext;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

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
