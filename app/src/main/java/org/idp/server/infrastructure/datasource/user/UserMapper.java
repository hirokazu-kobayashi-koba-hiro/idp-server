package org.idp.server.infrastructure.datasource.user;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.domain.model.tenant.Tenant;

@Mapper
public interface UserMapper {
  void insert(@Param("tenant") Tenant tenant, @Param("user") User user);

  User select(@Param("userId") String userId);

  User selectBy(
      @Param("tenant") Tenant tenant,
      @Param("email") String email,
      @Param("providerId") String providerId);

  List<User> selectList(
      @Param("tenant") Tenant tenantId, @Param("limit") int limit, @Param("offset") int offset);

  void update(@Param("user") User user, @Param("customProperties") String customProperties);

  User selectByProvider(String tokenIssuer, String providerId, String providerUserId);
}
