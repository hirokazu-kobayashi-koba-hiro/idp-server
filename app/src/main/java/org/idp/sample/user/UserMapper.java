package org.idp.sample.user;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.idp.server.oauth.identity.User;
import org.idp.server.type.oauth.TokenIssuer;

@Mapper
public interface UserMapper {
  void insert(@Param("tokenIssuer") TokenIssuer tokenIssuer, @Param("user") User user);

  User select(@Param("userId") String userId);
}
