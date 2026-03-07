FROM nginxinc/nginx-unprivileged:mainline-alpine-slim

# Switch to root to install packages
USER root


# Install JDK, curl, git
RUN apk add --no-cache openjdk17 curl git

# Copy the project
# NOTE: expecting the context to be the project root
COPY . /repo

# Build the API reference
WORKDIR /repo
RUN ./gradlew :dokkaGenerate

# Copy built site to nginx html directory
RUN cp -r /repo/build/dokka/html/* /usr/share/nginx/html/

# Remove the cloned repository
RUN rm -rf /repo


# Switch back to nginx user
USER nginx

WORKDIR /usr/share/nginx/html
