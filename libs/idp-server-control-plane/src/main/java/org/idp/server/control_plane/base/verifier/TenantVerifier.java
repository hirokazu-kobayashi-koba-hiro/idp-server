package org.idp.server.control_plane.base.verifier;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantQueryRepository;

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
