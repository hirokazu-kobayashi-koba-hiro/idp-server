
## tisp

### self

use OpenSSL  openssl. not LibreSSL

```shell
CN=test.example.com
date=$(date +%Y%m%d%H%M%S)
openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 > ${date}_private_key.pem
openssl pkey -pubout -in ${date}_private_key.pem > ${date}_public_key.pem
openssl req -x509 -key ${date}_private_key.pem -subj /CN=${CN} -days 1000 > ${date}_certificate.pem
```

