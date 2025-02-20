package org.idp.sample.domain.model.user;

public interface PasswordEncodeDelegation {

  String encode(String rawPassword);
}
