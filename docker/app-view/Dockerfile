FROM node:20-alpine

WORKDIR /app

# Install dependencies
COPY app-view/package*.json ./
RUN npm ci --only=production

# Copy source code
COPY app-view/ .

# Build Next.js application
RUN npm run build

# Expose port
EXPOSE 3000

# Start Next.js server
CMD ["npm", "start"]
