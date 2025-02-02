package org.idp.sample.user;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.idp.sample.Tenant;
import org.idp.server.oauth.identity.User;

import java.util.List;

@Mapper
public interface UserMapper {
  void insert(@Param("tenant") Tenant tenant, @Param("user") User user);

  User select(@Param("userId") String userId);

  User selectBy(@Param("tenantId") String tenantId, @Param("email") String email);

  List<User> selectList(@Param("tenantId") String tenantId, @Param("limit") int limit, @Param("offset") int offset);
}
