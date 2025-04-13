package org.idp.server.core.adapters.datasource.identity;

import java.util.List;
import java.util.Map;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.tenant.Tenant;

public interface UserSqlExecutor {
  void insert(Tenant tenant, User user);

  Map<String, String> selectOne(Tenant tenant, String userId);

  Map<String, String> selectByEmail(Tenant tenant, String email, String providerId);

  List<Map<String, String>> selectList(Tenant tenant, int limit, int offset);

  void update(Tenant tenant, User user);

  Map<String, String> selectByProvider(Tenant tenant, String providerId, String providerUserId);
}
