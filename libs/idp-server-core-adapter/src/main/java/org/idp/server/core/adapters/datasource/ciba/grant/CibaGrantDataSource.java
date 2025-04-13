package org.idp.server.core.adapters.datasource.ciba.grant;

import java.util.Map;
import java.util.Objects;
import org.idp.server.core.ciba.grant.CibaGrant;
import org.idp.server.core.ciba.repository.CibaGrantRepository;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.type.ciba.AuthReqId;

public class CibaGrantDataSource implements CibaGrantRepository {

  CibaGrantSqlExecutors executors;

  public CibaGrantDataSource() {
    this.executors = new CibaGrantSqlExecutors();
  }

  @Override
  public void register(Tenant tenant, CibaGrant cibaGrant) {
    CibaGrantSqlExecutor executor = executors.get(tenant.dialect());
    executor.insert(cibaGrant);
  }

  @Override
  public void update(Tenant tenant, CibaGrant cibaGrant) {
    CibaGrantSqlExecutor executor = executors.get(tenant.dialect());
    executor.update(cibaGrant);
  }

  @Override
  public CibaGrant find(Tenant tenant, AuthReqId authReqId) {
    CibaGrantSqlExecutor executor = executors.get(tenant.dialect());

    Map<String, String> stringMap = executor.selectOne(authReqId);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      return new CibaGrant();
    }

    return ModelConverter.convert(stringMap);
  }

  @Override
  public void delete(Tenant tenant, CibaGrant cibaGrant) {
    CibaGrantSqlExecutor executor = executors.get(tenant.dialect());
    executor.delete(cibaGrant);
  }
}
