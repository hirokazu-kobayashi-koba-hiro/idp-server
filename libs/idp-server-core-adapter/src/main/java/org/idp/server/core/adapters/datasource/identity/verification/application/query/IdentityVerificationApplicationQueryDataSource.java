package org.idp.server.core.adapters.datasource.identity.verification.application.query;

import java.util.List;
import java.util.Map;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.verification.application.*;
import org.idp.server.core.identity.verification.delegation.ExternalWorkflowApplicationIdentifier;
import org.idp.server.core.identity.verification.exception.IdentityVerificationApplicationNotFoundException;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class IdentityVerificationApplicationQueryDataSource
    implements IdentityVerificationApplicationQueryRepository {

  IdentityVerificationApplicationQuerySqlExecutors executors;

  public IdentityVerificationApplicationQueryDataSource() {
    this.executors = new IdentityVerificationApplicationQuerySqlExecutors();
  }

  @Override
  public IdentityVerificationApplication get(
      Tenant tenant, User user, IdentityVerificationApplicationIdentifier identifier) {
    IdentityVerificationApplicationQuerySqlExecutor executor = executors.get(tenant.databaseType());

    Map<String, String> result = executor.selectOne(tenant, user, identifier);

    if (result == null || result.isEmpty()) {
      throw new IdentityVerificationApplicationNotFoundException(
          String.format("IdentityVerificationApplication not found (%s)", identifier.value()));
    }

    return ModelConverter.convert(result);
  }

  @Override
  public IdentityVerificationApplication get(
      Tenant tenant, ExternalWorkflowApplicationIdentifier identifier) {
    IdentityVerificationApplicationQuerySqlExecutor executor = executors.get(tenant.databaseType());

    Map<String, String> result = executor.selectOne(tenant, identifier);

    if (result == null || result.isEmpty()) {
      throw new IdentityVerificationApplicationNotFoundException(
          String.format("IdentityVerificationApplication not found (%s)", identifier.value()));
    }

    return ModelConverter.convert(result);
  }

  @Override
  public IdentityVerificationApplications findAll(Tenant tenant, User user) {

    IdentityVerificationApplicationQuerySqlExecutor executor = executors.get(tenant.databaseType());

    List<Map<String, String>> result = executor.selectList(tenant, user);

    if (result == null || result.isEmpty()) {
      return new IdentityVerificationApplications();
    }

    List<IdentityVerificationApplication> applicationList =
        result.stream().map(ModelConverter::convert).toList();
    return new IdentityVerificationApplications(applicationList);
  }

  @Override
  public IdentityVerificationApplications findList(
      Tenant tenant, User user, IdentityVerificationApplicationQueries queries) {
    IdentityVerificationApplicationQuerySqlExecutor executor = executors.get(tenant.databaseType());

    List<Map<String, String>> result = executor.selectList(tenant, user, queries);

    if (result == null || result.isEmpty()) {
      return new IdentityVerificationApplications();
    }

    List<IdentityVerificationApplication> applicationList =
        result.stream().map(ModelConverter::convert).toList();
    return new IdentityVerificationApplications(applicationList);
  }
}
