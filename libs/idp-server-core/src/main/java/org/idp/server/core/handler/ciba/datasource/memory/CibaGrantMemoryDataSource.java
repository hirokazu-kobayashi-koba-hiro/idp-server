package org.idp.server.core.handler.ciba.datasource.memory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import org.idp.server.core.ciba.grant.CibaGrant;
import org.idp.server.core.ciba.repository.CibaGrantRepository;
import org.idp.server.core.type.ciba.AuthReqId;

public class CibaGrantMemoryDataSource implements CibaGrantRepository {

  Map<AuthReqId, CibaGrant> map = new HashMap<>();
  Logger log = Logger.getLogger(CibaGrantMemoryDataSource.class.getName());

  @Override
  public void register(CibaGrant cibaGrant) {
    map.put(cibaGrant.authReqId(), cibaGrant);
  }

  @Override
  public void update(CibaGrant cibaGrant) {
    map.put(cibaGrant.authReqId(), cibaGrant);
  }

  @Override
  public CibaGrant find(AuthReqId authReqId) {
    log.info("find auth_req_id: " + authReqId.value());
    CibaGrant cibaGrant = map.get(authReqId);
    if (Objects.isNull(cibaGrant)) {
      return new CibaGrant();
    }
    return cibaGrant;
  }

  @Override
  public void delete(CibaGrant cibaGrant) {
    log.info("delete auth_req_id: " + cibaGrant.authReqId().value());
    map.remove(cibaGrant.authReqId());
  }
}
