package org.idp.server.ciba.repository;

import org.idp.server.ciba.grant.CibaGrant;
import org.idp.server.type.ciba.AuthReqId;

public interface CibaGrantRepository {

  void register(CibaGrant cibaGrant);

  CibaGrant find(AuthReqId authReqId);

  void delete(CibaGrant cibaGrant);
}
