FROM node:20

COPY ./index.js .
COPY ./package.json .
COPY ./package-lock.json .
RUN npm install
EXPOSE 3000
CMD [ "node", "index.js" ]