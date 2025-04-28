package org.idp.server.core.adapters.datasource.identity.trustframework.application.query;

import java.util.Map;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplication;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplicationQueryRepository;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplications;
import org.idp.server.core.identity.verification.exception.IdentityVerificationApplicationNotFoundException;
import org.idp.server.core.tenant.Tenant;

public class IdentityVerificationApplicationQueryDataSource
    implements IdentityVerificationApplicationQueryRepository {

  IdentityVerificationApplicationQuerySqlExecutors executors;

  public IdentityVerificationApplicationQueryDataSource() {
    this.executors = new IdentityVerificationApplicationQuerySqlExecutors();
  }

  @Override
  public IdentityVerificationApplication get(
      Tenant tenant, IdentityVerificationApplicationIdentifier identifier) {
    IdentityVerificationApplicationQuerySqlExecutor executor = executors.get(tenant.databaseType());

    Map<String, String> result = executor.selectOne(tenant, identifier);

    if (result == null || result.isEmpty()) {
      throw new IdentityVerificationApplicationNotFoundException(
          String.format("IdentityVerificationApplication not found (%s)", identifier.value()));
    }

    return ModelConverter.convert(result);
  }

  @Override
  public IdentityVerificationApplications getAll(Tenant tenant, User user) {

    return new IdentityVerificationApplications();
  }
}
