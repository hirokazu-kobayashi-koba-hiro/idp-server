package org.idp.server.adapters.springboot.infrastructure.datasource.authentication;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.idp.server.authenticators.webauthn.WebAuthnCredential;

@Mapper
public interface WebAuthnCredentialMapper {

  void insert(@Param("credential") WebAuthnCredential credential);

  WebAuthnCredential selectBy(@Param("credentialId") String credentialId);

  List<WebAuthnCredential> selectByUserId(@Param("userId") String userId);
}
