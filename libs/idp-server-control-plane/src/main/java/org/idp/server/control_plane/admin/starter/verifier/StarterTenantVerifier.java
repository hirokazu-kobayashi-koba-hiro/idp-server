package org.idp.server.control_plane.admin.starter.verifier;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.control_plane.base.verifier.VerificationResult;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;

public class StarterTenantVerifier {

  TenantQueryRepository tenantQueryRepository;

  public StarterTenantVerifier(TenantQueryRepository tenantQueryRepository) {
    this.tenantQueryRepository = tenantQueryRepository;
  }

  public VerificationResult verify(Tenant tenant) {
    Tenant admin = tenantQueryRepository.findAdmin();

    List<String> errors = new ArrayList<>();
    if (admin.exists()) {
      errors.add("Admin Tenant already exists");
    }

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
