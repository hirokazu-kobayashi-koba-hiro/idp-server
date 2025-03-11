package org.idp.server.adapters.springboot.domain.model.user;

public interface PasswordVerificationDelegation {

  boolean verify(String rawPassword, String encodedPassword);
}
