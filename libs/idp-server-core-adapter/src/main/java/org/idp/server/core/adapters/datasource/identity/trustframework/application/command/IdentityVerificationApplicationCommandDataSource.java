package org.idp.server.core.adapters.datasource.identity.trustframework.application.command;

import org.idp.server.core.identity.trustframework.IdentityVerificationApplication;
import org.idp.server.core.identity.trustframework.IdentityVerificationApplicationCommandRepository;
import org.idp.server.core.tenant.Tenant;

public class IdentityVerificationApplicationCommandDataSource
    implements IdentityVerificationApplicationCommandRepository {

  @Override
  public void register(Tenant tenant, IdentityVerificationApplication application) {}

  @Override
  public void update(Tenant tenant, IdentityVerificationApplication application) {}
}
