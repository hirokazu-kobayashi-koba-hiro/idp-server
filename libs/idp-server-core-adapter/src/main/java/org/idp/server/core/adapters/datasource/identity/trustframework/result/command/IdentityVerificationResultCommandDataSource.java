package org.idp.server.core.adapters.datasource.identity.trustframework.result.command;

import org.idp.server.core.identity.verification.result.IdentityVerificationResult;
import org.idp.server.core.identity.verification.result.IdentityVerificationResultCommandRepository;
import org.idp.server.core.tenant.Tenant;

public class IdentityVerificationResultCommandDataSource
    implements IdentityVerificationResultCommandRepository {

  @Override
  public void register(Tenant tenant, IdentityVerificationResult result) {}
}
