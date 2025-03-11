package org.idp.server.adapters.springboot.domain.model.user;

public interface PasswordEncodeDelegation {

  String encode(String rawPassword);
}
