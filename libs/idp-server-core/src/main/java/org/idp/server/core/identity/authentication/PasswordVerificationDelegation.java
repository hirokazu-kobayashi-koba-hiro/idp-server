package org.idp.server.core.identity.authentication;

public interface PasswordVerificationDelegation {

  boolean verify(String rawPassword, String encodedPassword);
}
