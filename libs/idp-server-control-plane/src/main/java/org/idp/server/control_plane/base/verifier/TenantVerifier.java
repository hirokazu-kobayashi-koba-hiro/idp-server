/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.control_plane.base.verifier;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;

public class TenantVerifier {

  TenantQueryRepository tenantQueryRepository;

  public TenantVerifier(TenantQueryRepository tenantQueryRepository) {
    this.tenantQueryRepository = tenantQueryRepository;
  }

  public VerificationResult verify(Tenant tenant) {
    List<String> errors = new ArrayList<>();

    Tenant existing = tenantQueryRepository.find(tenant.identifier());
    if (existing.exists()) {
      errors.add("Tenant already exists");
    }

    if (!errors.isEmpty()) {
      return VerificationResult.failure(errors);
    }

    return VerificationResult.success();
  }
}
