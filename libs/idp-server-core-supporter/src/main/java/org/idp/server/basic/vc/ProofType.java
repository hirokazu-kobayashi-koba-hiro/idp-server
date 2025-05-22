/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.vc;

public enum ProofType {
  BbsBlsSignature2020,
  EcdsaKoblitzSignature2016,
  EcdsaSecp256k1Signature2019,
  Ed25519Signature2018,
  Ed25519Signature2020,
  JcsEcdsaSecp256k1Signature2019,
  JcsEd25519Signature2020,
  JsonWebSignature2020,
  RsaSignature2018
}
