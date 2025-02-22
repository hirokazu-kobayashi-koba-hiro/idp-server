## tips

uuid

```shell
uuidgen | tr 'A-Z' 'a-z'
```

### signup

```shell
curl -v 'http://localhost:8080/67e7eae6-62b0-4500-9eff-87459f63fc66/api/v1/authorizations?prompt=create&scope=openid+profile+phone+emailaccount+transfers&response_type=code&client_id=clientSecretPost&redirect_uri=https%3A%2F%2Fwww.certification.openid.net%2Ftest%2Fa%2Fidp_oidc_basic%2Fcallback&state=aiueo&organization_id=123&organization_name=test'
```

### signin

```shell
curl -v 'http://localhost:8080/67e7eae6-62b0-4500-9eff-87459f63fc66/api/v1/authorizations?prompt=login&scope=openid+profile+phone+emailaccount+transfers&response_type=code&client_id=clientSecretPost&redirect_uri=https%3A%2F%2Fwww.certification.openid.net%2Ftest%2Fa%2Fidp_oidc_basic%2Fcallback&state=aiueo&organization_id=123&organization_name=test'
```