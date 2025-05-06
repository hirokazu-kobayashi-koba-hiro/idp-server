package org.idp.server.core.adapters.datasource.ciba.grant;

import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.type.ciba.AuthReqId;
import org.idp.server.core.ciba.exception.CibaGrantNotFoundException;
import org.idp.server.core.ciba.grant.CibaGrant;
import org.idp.server.core.ciba.repository.CibaGrantRepository;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class CibaGrantDataSource implements CibaGrantRepository {

  CibaGrantSqlExecutors executors;

  public CibaGrantDataSource() {
    this.executors = new CibaGrantSqlExecutors();
  }

  @Override
  public void register(Tenant tenant, CibaGrant cibaGrant) {
    CibaGrantSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(cibaGrant);
  }

  @Override
  public void update(Tenant tenant, CibaGrant cibaGrant) {
    CibaGrantSqlExecutor executor = executors.get(tenant.databaseType());
    executor.update(cibaGrant);
  }

  @Override
  public CibaGrant find(Tenant tenant, AuthReqId authReqId) {
    CibaGrantSqlExecutor executor = executors.get(tenant.databaseType());

    Map<String, String> stringMap = executor.selectOne(authReqId);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      return new CibaGrant();
    }

    return ModelConverter.convert(stringMap);
  }

  @Override
  public CibaGrant get(Tenant tenant, BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier) {
    CibaGrantSqlExecutor executor = executors.get(tenant.databaseType());

    Map<String, String> stringMap = executor.selectOne(backchannelAuthenticationRequestIdentifier);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      throw new CibaGrantNotFoundException(String.format("ciba grant not found (%s)", backchannelAuthenticationRequestIdentifier.value()));
    }

    return ModelConverter.convert(stringMap);
  }

  @Override
  public void delete(Tenant tenant, CibaGrant cibaGrant) {
    CibaGrantSqlExecutor executor = executors.get(tenant.databaseType());
    executor.delete(cibaGrant);
  }
}
