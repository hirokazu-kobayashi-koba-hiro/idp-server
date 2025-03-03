package org.idp.server.domain.model.user;

public interface PasswordEncodeDelegation {

  String encode(String rawPassword);
}
