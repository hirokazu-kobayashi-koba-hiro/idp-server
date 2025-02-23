package org.idp.sample.infrastructure.datasource.authentication;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.idp.sample.subdomain.webauthn.WebAuthnCredential;

@Mapper
public interface WebAuthnCredentialMapper {

  void insert(@Param("credential") WebAuthnCredential credential);

  WebAuthnCredential selectBy(@Param("credentialId") String credentialId);

  List<WebAuthnCredential> selectByUserId(@Param("userId") String userId);
}
