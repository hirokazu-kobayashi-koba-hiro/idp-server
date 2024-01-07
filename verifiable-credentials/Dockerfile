FROM python:3.8

#cert-ssuer
RUN apt update && apt-get install gcc -y

COPY ./ethereum_requirements.txt .

RUN python3 -m ensurepip \
    && pip3 install --upgrade pip setuptools \
    && pip3 install Cython \
    && pip3 install wheel \
    && mkdir -p /etc/cert-issuer/data/unsigned_certificates \
    && mkdir /etc/cert-issuer/data/blockchain_certificates \
    && pip3 install cert-issuer \
    && pip3 install -r ethereum_requirements.txt

COPY ./__init__.py /usr/local/lib/python3.8/site-packages/cert_issuer/blockchain_handlers/ethereum/__init__.py

#api app
RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
    apt-get install -y nodejs

COPY ./package.json .
COPY ./package-lock.json .
RUN npm install
COPY index.js .
EXPOSE 3000

CMD [ "node", "index.js" ]
