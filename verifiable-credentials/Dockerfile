FROM node:20

COPY ./package.json .
COPY ./package-lock.json .
RUN npm install
COPY Encoder.js ./node_modules/@vaultie/lds-merkle-proof-2019/src/Encoder.js
COPY index.js .
COPY cert.js .
COPY ethereum.js .
EXPOSE 3000

CMD [ "node", "index.js" ]
