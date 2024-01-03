FROM nikolaik/python-nodejs:python3.8-nodejs21-slim

#cert-ssuer
RUN apt update
RUN apt-get install gcc -y

COPY ./ethereum_requirements.txt .
COPY ./privateKey.txt .

RUN python3 -m ensurepip \
    && pip3 install --upgrade pip setuptools \
    && pip3 install Cython \
    && pip3 install wheel \
    && mkdir -p /etc/cert-issuer/data/unsigned_certificates \
    && mkdir /etc/cert-issuer/data/blockchain_certificates \
    && pip3 install cert-issuer \
    && pip3 install -r ethereum_requirements.txt

#api app
COPY ./index.js .
COPY ./package.json .
COPY ./package-lock.json .
RUN npm install
EXPOSE 3000

CMD [ "node", "index.js" ]
