package org.idp.server.core.api;

import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.organization.Organization;
import org.idp.server.core.organization.OrganizationName;
import org.idp.server.core.tenant.ServerDomain;
import org.idp.server.core.tenant.TenantName;

import java.util.Map;

public interface OnboardingApi {

  Map<String, Object> initialize(
      User operator,
      Map<String, Object> request);
}
