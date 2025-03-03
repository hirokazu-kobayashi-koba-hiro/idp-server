package org.idp.server.infrastructure.datasource.authentication;

import java.util.List;
import org.idp.server.subdomain.webauthn.WebAuthnCredential;
import org.idp.server.subdomain.webauthn.WebAuthnCredentialNotFoundException;
import org.idp.server.subdomain.webauthn.WebAuthnCredentialRepository;
import org.idp.server.subdomain.webauthn.WebAuthnCredentials;
import org.springframework.stereotype.Repository;

@Repository
public class WebAuthnCredentialDataSource implements WebAuthnCredentialRepository {

  WebAuthnCredentialMapper mapper;

  public WebAuthnCredentialDataSource(WebAuthnCredentialMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void register(WebAuthnCredential credential) {
    mapper.insert(credential);
  }

  @Override
  public WebAuthnCredential get(String credentialId) {
    WebAuthnCredential credential = mapper.selectBy(credentialId);

    if (credential == null) {
      throw new WebAuthnCredentialNotFoundException(
          String.format("credential not found (%s)", credentialId));
    }

    return credential;
  }

  @Override
  public WebAuthnCredentials findAll(String userId) {
    List<WebAuthnCredential> credentials = mapper.selectByUserId(userId);

    if (credentials == null || credentials.isEmpty()) {
      return new WebAuthnCredentials();
    }

    return new WebAuthnCredentials(credentials);
  }

  @Override
  public void updateSignCount(String credentialId, long signCount) {}

  @Override
  public void delete(String credentialId) {}
}
