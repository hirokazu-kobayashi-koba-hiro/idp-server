package org.idp.server.core.extension.ciba.repository;

import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface BackchannelAuthenticationRequestRepository {

  void register(Tenant tenant, BackchannelAuthenticationRequest request);

  BackchannelAuthenticationRequest find(
      Tenant tenant, BackchannelAuthenticationRequestIdentifier identifier);

  void delete(Tenant tenant, BackchannelAuthenticationRequestIdentifier identifier);
}
