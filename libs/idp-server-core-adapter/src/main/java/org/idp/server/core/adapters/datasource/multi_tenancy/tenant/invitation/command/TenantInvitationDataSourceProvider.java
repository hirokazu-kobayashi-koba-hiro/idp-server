/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.invitation.command;

import org.idp.server.control_plane.management.tenant.invitation.operation.TenantInvitationCommandRepository;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;

public class TenantInvitationDataSourceProvider
    implements ApplicationComponentProvider<TenantInvitationCommandRepository> {

  @Override
  public Class<TenantInvitationCommandRepository> type() {
    return TenantInvitationCommandRepository.class;
  }

  @Override
  public TenantInvitationCommandRepository provide(
      ApplicationComponentDependencyContainer container) {
    return new TenantInvitationCommandDataSource();
  }
}
