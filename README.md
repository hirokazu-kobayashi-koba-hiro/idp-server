# idp-server

## architecture

![architecture](./architecture.svg)

## supported spec

1. RFC6749 The OAuth 2.0 Authorization Framework
   1. authorization code flow
   2. implicit flow
2. OpenID Connect Core 1.0 incorporating errata set 1
   1. authorization code flow
   2. implicit flow
   3. hybrid flow
   4. request object
   5. userinfo
3. RFC7009 OAuth 2.0 Token Revocation
4. RFC7662 OAuth 2.0 Token Introspection

## supported client authentication

1. client_secret_post
2. client_secret_basic
3. client_secret_jwt
4. private_key_jwt

