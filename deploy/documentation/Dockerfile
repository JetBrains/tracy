FROM nginxinc/nginx-unprivileged:mainline-alpine-slim

# Switch to root to install packages
USER root


# Install Python 3.11 and curl
RUN apk add --no-cache python3 py3-pip curl git

# Install uv & add it to PATH
RUN curl -LsSf https://astral.sh/uv/install.sh | sh
ENV PATH="/root/.cargo/bin:$PATH"

# Copy the project
PROJECT_ROOT="../../"
COPY $PROJECT_ROOT /repo

# Build the documentation
WORKDIR /repo/docs
RUN uv sync --frozen --all-extras
RUN uv run mkdocs build

# Copy built site to nginx html directory
RUN cp -r /repo/docs/site/* /usr/share/nginx/html/

# Remove the cloned repository
RUN rm -rf /repo


# Switch back to nginx user
USER nginx

WORKDIR /usr/share/nginx/html
