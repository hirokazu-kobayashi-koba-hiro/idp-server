package org.idp.server.core.user;

public interface PasswordVerificationDelegation {

  boolean verify(String rawPassword, String encodedPassword);
}
