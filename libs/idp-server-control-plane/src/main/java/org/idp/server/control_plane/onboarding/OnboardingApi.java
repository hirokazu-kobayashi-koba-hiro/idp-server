package org.idp.server.control_plane.onboarding;

import org.idp.server.control_plane.onboarding.io.OnboardingRequest;
import org.idp.server.control_plane.onboarding.io.OnboardingResponse;
import org.idp.server.core.identity.User;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;

public interface OnboardingApi {

  OnboardingResponse onboard(
      TenantIdentifier adminTenantIdentifier, User operator, OnboardingRequest request);
}
