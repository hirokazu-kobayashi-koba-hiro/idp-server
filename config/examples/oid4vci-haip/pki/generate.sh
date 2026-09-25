#!/bin/bash
# OID4VCI HAIP 適合性テスト用の PKI を作る。生成物はコミットする（テスト専用の鍵）。
#
#   attester-*  suite が Wallet Provider（Client Attester）として Client Attestation に署名する鍵。
#               suite の client_attestation.attester_jwks に秘密鍵 JWK（x5c 付き）を、
#               idp-server のクライアント設定に公開鍵 JWKS を入れる。
#   issuer-*    idp-server が SD-JWT VC に署名する鍵。HAIP は x5c を要求し、末端の証明書は
#               CA が発行したもので、ルートは x5c に含めない。suite の credential.trust_anchor_pem に
#               issuer-ca.pem を入れる。
#
# 使い方: ./generate.sh   （既存のファイルは上書きする）

set -euo pipefail
cd "$(dirname "$0")"

DAYS=3650

new_ca() { # name subject
  openssl ecparam -name prime256v1 -genkey -noout -out "$1-ca.key"
  openssl req -x509 -new -key "$1-ca.key" -sha256 -days "$DAYS" -subj "$2" \
    -addext "basicConstraints=critical,CA:TRUE" \
    -addext "keyUsage=critical,keyCertSign,cRLSign" \
    -out "$1-ca.pem"
}

new_leaf() { # name subject
  openssl ecparam -name prime256v1 -genkey -noout -out "$1.key"
  openssl req -new -key "$1.key" -subj "$2" -out "$1.csr"
  printf "basicConstraints=critical,CA:FALSE\nkeyUsage=critical,digitalSignature\n" > "$1.ext"
  openssl x509 -req -in "$1.csr" -CA "$1-ca.pem" -CAkey "$1-ca.key" -CAcreateserial \
    -sha256 -days "$DAYS" -extfile "$1.ext" -out "$1.pem"
  rm -f "$1.csr" "$1.ext" "$1-ca.srl"
}

new_ca attester "/CN=idp-server OID4VCI conformance Wallet Provider CA"
new_leaf attester "/CN=idp-server OID4VCI conformance Wallet Provider"
new_ca issuer "/CN=idp-server OID4VCI conformance Credential Issuer CA"
new_leaf issuer "/CN=idp-server OID4VCI conformance Credential Issuer"

node ./to-jwk.mjs attester attester-signing-key > attester.jwk.json
node ./to-jwk.mjs issuer credential-signing-key > issuer.jwk.json
echo "generated: attester-ca.pem attester.pem attester.jwk.json issuer-ca.pem issuer.pem issuer.jwk.json"
