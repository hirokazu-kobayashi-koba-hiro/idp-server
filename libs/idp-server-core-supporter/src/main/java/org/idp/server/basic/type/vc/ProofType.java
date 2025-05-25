/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.basic.type.vc;

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
