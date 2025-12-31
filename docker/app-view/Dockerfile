FROM node:20-alpine

WORKDIR /app

# Build arguments for Next.js static export
ARG BASE_PATH=/auth-views
ARG NEXT_PUBLIC_BACKEND_URL=

# Set environment variables for build time
ENV BASE_PATH=${BASE_PATH}
ENV NEXT_PUBLIC_BACKEND_URL=${NEXT_PUBLIC_BACKEND_URL}

# Install dependencies
COPY app-view/package*.json ./
RUN npm ci

# Copy source code
COPY app-view/ .

# Build Next.js application (creates static export in 'out' directory)
RUN npm run build

# Install serve globally for static file hosting
RUN npm install -g serve

# Expose port
EXPOSE 3000

# Serve static files using serve.json configuration
CMD ["serve", "-l", "3000"]
