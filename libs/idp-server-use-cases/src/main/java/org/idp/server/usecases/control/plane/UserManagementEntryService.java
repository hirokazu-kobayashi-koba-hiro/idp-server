package org.idp.server.usecases.control.plane;

import java.util.List;
import org.idp.server.basic.datasource.Transaction;
import org.idp.server.control.plane.UserManagementApi;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserIdentifier;
import org.idp.server.core.identity.repository.UserQueryRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantRepository;

// TODO
@Transaction
public class UserManagementEntryService implements UserManagementApi {

  TenantRepository tenantRepository;
  UserQueryRepository userQueryRepository;

  public UserManagementEntryService(
      TenantRepository tenantRepository, UserQueryRepository userQueryRepository) {
    this.tenantRepository = tenantRepository;
    this.userQueryRepository = userQueryRepository;
  }

  @Override
  public void register(TenantIdentifier tenantIdentifier, User user) {
    Tenant tenant = tenantRepository.get(tenantIdentifier);
    userQueryRepository.register(tenant, user);
  }

  @Override
  public User get(TenantIdentifier tenantIdentifier, UserIdentifier userIdentifier) {
    Tenant tenant = tenantRepository.get(tenantIdentifier);

    return userQueryRepository.get(tenant, userIdentifier);
  }

  @Override
  public User findBy(TenantIdentifier tenantIdentifier, String email, String providerId) {
    Tenant tenant = tenantRepository.get(tenantIdentifier);
    return userQueryRepository.findByEmail(tenant, email, providerId);
  }

  @Override
  public void update(TenantIdentifier tenantIdentifier, User user) {
    Tenant tenant = tenantRepository.get(tenantIdentifier);
    userQueryRepository.update(tenant, user);
  }

  @Override
  public List<User> find(TenantIdentifier tenantIdentifier, int limit, int offset) {
    Tenant tenant = tenantRepository.get(tenantIdentifier);

    return userQueryRepository.findList(tenant, limit, offset);
  }
}
