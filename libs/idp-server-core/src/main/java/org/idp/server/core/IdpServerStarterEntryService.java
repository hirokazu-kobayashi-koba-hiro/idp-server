package org.idp.server.core;

import java.util.Map;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.api.IdpServerStarterApi;
import org.idp.server.core.handler.configuration.ClientConfigurationHandler;
import org.idp.server.core.organization.OrganizationService;
import org.idp.server.core.tenant.TenantRepository;
import org.idp.server.core.user.UserRepository;

@Transactional
public class IdpServerStarterEntryService implements IdpServerStarterApi {

  OrganizationService organizationService;
  TenantRepository tenantRepository;
  ClientConfigurationHandler clientConfigurationHandler;
  UserRepository userRepository;

  @Override
  public Map<String, Object> initialize(Map<String, Object> request) {

    return Map.of();
  }
}
