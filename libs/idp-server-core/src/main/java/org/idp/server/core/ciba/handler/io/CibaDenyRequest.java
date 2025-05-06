package org.idp.server.core.ciba.handler.io;

import org.idp.server.core.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class CibaDenyRequest {
  Tenant tenant;
  BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier;

  public CibaDenyRequest(Tenant tenant, BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier) {
    this.tenant = tenant;
    this.backchannelAuthenticationRequestIdentifier = backchannelAuthenticationRequestIdentifier;
  }

  public BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier() {
    return backchannelAuthenticationRequestIdentifier;
  }

  public Tenant tenant() {
    return tenant;
  }
}
