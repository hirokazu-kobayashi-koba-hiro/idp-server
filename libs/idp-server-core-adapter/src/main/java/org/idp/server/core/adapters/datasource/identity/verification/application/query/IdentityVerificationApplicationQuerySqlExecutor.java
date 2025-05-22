/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.identity.verification.application.query;

import java.util.List;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplicationQueries;
import org.idp.server.core.extension.identity.verification.delegation.ExternalWorkflowApplicationIdentifier;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface IdentityVerificationApplicationQuerySqlExecutor {
  Map<String, String> selectOne(
      Tenant tenant, User user, IdentityVerificationApplicationIdentifier identifier);

  Map<String, String> selectOne(Tenant tenant, ExternalWorkflowApplicationIdentifier identifier);

  List<Map<String, String>> selectList(Tenant tenant, User user);

  List<Map<String, String>> selectList(
      Tenant tenant, User user, IdentityVerificationApplicationQueries queries);
}
