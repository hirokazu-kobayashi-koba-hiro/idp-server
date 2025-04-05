package org.idp.server.core.mfa;

public interface MfaTransactionCommandRepository {

  <T> void register(MfaTransactionIdentifier identifier, String key, T payload);
}
