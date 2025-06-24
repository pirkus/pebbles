# Cursor Environment Automatic Setup

This project uses Cursor's built-in environment configuration for automatic setup when the background agent starts.

## Configuration File

The environment is configured in `.cursor/environment.json` which is automatically executed by Cursor.

## What Happens on Startup

When the background agent starts, Cursor automatically:

1. **Runs the `install` command** which:
   - Verifies Clojure is installed
   - Installs Docker and Docker Compose if missing
   - Confirms all dependencies are ready

2. **Runs the `start` command** which:
   - Starts Docker daemon if not running
   - Starts MongoDB container using Docker Compose
   - Confirms services are running

3. **Sets environment variables**:
   - `MONGO_URI=mongodb://localhost:27017/pebbles`
   - `PORT=8081`
   - `USE_EXISTING_MONGO=true`

4. **Opens configured terminals**:
   - MongoDB Status terminal showing container status

## Manual Commands

After the environment is set up, you can use these commands:

### Running Tests
```bash
# Tests run with a separate test database
USE_EXISTING_MONGO=true MONGO_URI=mongodb://localhost:27017/test clojure -X:test
```

### Starting the Application
```bash
clojure -M:run
```

### Quick Commands
The environment.json includes these quick commands:
- Check MongoDB status: `sudo docker ps | grep mongodb`
- View MongoDB logs: `sudo docker logs $(sudo docker ps -q -f name=mongodb) --tail 50`
- Access MongoDB shell: `sudo docker exec -it $(sudo docker ps -q -f name=mongodb) mongosh`

## Troubleshooting

If the automatic setup fails:

1. **Clojure not installed**: Install Clojure following https://clojure.org/guides/install_clojure
2. **Docker issues**: The setup will attempt to install Docker automatically, but you may need to restart your terminal
3. **MongoDB not starting**: Check Docker logs with the quick command above

## Environment Variables

The setup uses these environment variables:
- For development: `MONGO_URI=mongodb://localhost:27017/pebbles`
- For testing: `MONGO_URI=mongodb://localhost:27017/test`
- Both use: `USE_EXISTING_MONGO=true` to bypass Testcontainers

## Note

This setup is specifically designed for Cursor's background agents running on Linux. The configuration is in `.cursor/environment.json` and is automatically executed when a background agent starts - no manual intervention required!