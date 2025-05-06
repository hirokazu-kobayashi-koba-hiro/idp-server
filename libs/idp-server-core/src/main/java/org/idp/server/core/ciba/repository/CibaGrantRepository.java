package org.idp.server.core.ciba.repository;

import org.idp.server.basic.type.ciba.AuthReqId;
import org.idp.server.core.ciba.grant.CibaGrant;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface CibaGrantRepository {

  void register(Tenant tenant, CibaGrant cibaGrant);

  void update(Tenant tenant, CibaGrant cibaGrant);

  CibaGrant find(Tenant tenant, AuthReqId authReqId);

  CibaGrant get(Tenant tenant, BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier);

  void delete(Tenant tenant, CibaGrant cibaGrant);
}
