package org.idp.server.handler.ciba.datasource.memory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.ciba.grant.CibaGrant;
import org.idp.server.ciba.repository.CibaGrantRepository;
import org.idp.server.type.ciba.AuthReqId;

public class CibaGrantMemoryDataSource implements CibaGrantRepository {

  Map<AuthReqId, CibaGrant> map = new HashMap<>();

  @Override
  public void register(CibaGrant cibaGrant) {
    map.put(cibaGrant.authReqId(), cibaGrant);
  }

  @Override
  public CibaGrant find(AuthReqId authReqId) {
    CibaGrant cibaGrant = map.get(authReqId);
    if (Objects.isNull(cibaGrant)) {
      return new CibaGrant();
    }
    return cibaGrant;
  }

  @Override
  public void delete(CibaGrant cibaGrant) {
    map.remove(cibaGrant.authReqId());
  }
}
