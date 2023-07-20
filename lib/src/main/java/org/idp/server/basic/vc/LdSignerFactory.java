package org.idp.server.basic.vc;

import info.weboftrust.ldsignatures.signer.LdSigner;

public interface LdSignerFactory {
  LdSigner create(String privateKey) throws VcInvalidKeyException;
}
