package org.idp.server.core.ciba.repository;

import org.idp.server.core.ciba.grant.CibaGrant;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.type.ciba.AuthReqId;

public interface CibaGrantRepository {

  void register(Tenant tenant, CibaGrant cibaGrant);

  void update(Tenant tenant, CibaGrant cibaGrant);

  CibaGrant find(Tenant tenant, AuthReqId authReqId);

  void delete(Tenant tenant, CibaGrant cibaGrant);
}
