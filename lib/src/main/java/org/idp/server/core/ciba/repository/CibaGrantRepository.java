package org.idp.server.core.ciba.repository;

import org.idp.server.core.ciba.grant.CibaGrant;
import org.idp.server.core.type.ciba.AuthReqId;

public interface CibaGrantRepository {

  void register(CibaGrant cibaGrant);

  void update(CibaGrant cibaGrant);

  CibaGrant find(AuthReqId authReqId);

  void delete(CibaGrant cibaGrant);
}
