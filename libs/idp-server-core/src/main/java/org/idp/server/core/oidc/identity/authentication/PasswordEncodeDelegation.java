package org.idp.server.core.oidc.identity.authentication;

public interface PasswordEncodeDelegation {

  String encode(String rawPassword);
}
