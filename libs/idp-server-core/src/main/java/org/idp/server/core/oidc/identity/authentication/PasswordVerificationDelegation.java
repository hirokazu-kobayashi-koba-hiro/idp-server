package org.idp.server.core.oidc.identity.authentication;

public interface PasswordVerificationDelegation {

  boolean verify(String rawPassword, String encodedPassword);
}
