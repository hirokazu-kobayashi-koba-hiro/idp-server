package org.idp.server.core.mfa;

public interface MfaTransactionCommandRepository {

  <T> void register(MfaTransactionIdentifier identifier, String key, T payload);

  <T> void update(MfaTransactionIdentifier identifier, String key, T payload);
}
