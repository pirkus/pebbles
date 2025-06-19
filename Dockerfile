FROM clojure:temurin-17-tools-deps-alpine

WORKDIR /app

# Copy deps.edn first for better caching
COPY deps.edn ./

# Download dependencies
RUN clojure -P

# Copy source files
COPY src/ ./src/
COPY resources/ ./resources/

# Expose port
EXPOSE 8081

# Run the application
CMD ["clojure", "-M", "-m", "pebbles.system"]