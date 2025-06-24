#!/usr/bin/env node

/**
 * Cursor Environment Runner
 * Executes commands from .cursor/environment.json
 */

const fs = require('fs');
const { exec, execSync } = require('child_process');
const path = require('path');

const ENV_FILE = '.cursor/environment.json';

// Load environment configuration
function loadConfig() {
  try {
    const configPath = path.join(process.cwd(), ENV_FILE);
    if (!fs.existsSync(configPath)) {
      console.error(`Error: ${ENV_FILE} not found!`);
      process.exit(1);
    }
    return JSON.parse(fs.readFileSync(configPath, 'utf8'));
  } catch (error) {
    console.error(`Error loading ${ENV_FILE}:`, error.message);
    process.exit(1);
  }
}

// Execute a command with optional retries
async function executeCommand(cmd, options = {}) {
  const { name, command, required = false, retries = 0, retryDelay = 1, continueOnError = false } = cmd;
  
  console.log(`\n→ ${name || 'Running command'}:`);
  console.log(`  ${command}`);
  
  let attempts = 0;
  let lastError;
  
  while (attempts <= retries) {
    try {
      execSync(command, { stdio: 'inherit', shell: '/bin/bash' });
      console.log(`✓ ${name || 'Command'} completed successfully`);
      return true;
    } catch (error) {
      lastError = error;
      attempts++;
      
      if (attempts <= retries) {
        console.log(`⚠ Attempt ${attempts} failed, retrying in ${retryDelay}s...`);
        await new Promise(resolve => setTimeout(resolve, retryDelay * 1000));
      }
    }
  }
  
  if (required && !continueOnError) {
    console.error(`✗ ${name || 'Command'} failed after ${attempts} attempts`);
    process.exit(1);
  } else if (!continueOnError) {
    console.warn(`⚠ ${name || 'Command'} failed but continuing...`);
  }
  
  return false;
}

// Run startup commands
async function runStartup(config) {
  console.log('🚀 Running startup commands...\n');
  
  if (!config.startup || !config.startup.commands) {
    console.log('No startup commands defined');
    return;
  }
  
  for (const cmd of config.startup.commands) {
    if (typeof cmd === 'string') {
      await executeCommand({ command: cmd });
    } else {
      await executeCommand(cmd);
    }
  }
  
  console.log('\n✅ Startup completed!');
}

// Run a specific task
async function runTask(config, taskName) {
  const task = config.tasks?.[taskName];
  
  if (!task) {
    console.error(`Error: Task '${taskName}' not found`);
    console.log('\nAvailable tasks:');
    Object.keys(config.tasks || {}).forEach(name => {
      console.log(`  - ${name}: ${config.tasks[name].description || 'No description'}`);
    });
    process.exit(1);
  }
  
  console.log(`\n📋 Running task: ${taskName}`);
  console.log(`   ${task.description || 'No description'}\n`);
  
  // Set environment variables for the task
  if (task.environmentVariables) {
    Object.entries(task.environmentVariables).forEach(([key, value]) => {
      process.env[key] = value;
    });
  }
  
  // Execute commands
  for (const cmd of task.commands || []) {
    await executeCommand({ command: cmd });
  }
  
  console.log(`\n✅ Task '${taskName}' completed!`);
}

// Main execution
async function main() {
  const args = process.argv.slice(2);
  const config = loadConfig();
  
  if (args.length === 0 || args[0] === 'startup') {
    await runStartup(config);
  } else if (args[0] === 'task' && args[1]) {
    await runTask(config, args[1]);
  } else if (args[0] === 'help') {
    console.log('Cursor Environment Runner\n');
    console.log('Usage:');
    console.log('  node cursor-env-runner.js [command] [options]\n');
    console.log('Commands:');
    console.log('  startup        Run startup commands (default)');
    console.log('  task <name>    Run a specific task');
    console.log('  help           Show this help message\n');
    console.log('Available tasks:');
    Object.keys(config.tasks || {}).forEach(name => {
      console.log(`  - ${name}: ${config.tasks[name].description || 'No description'}`);
    });
  } else {
    console.error('Invalid command. Use "help" for usage information.');
    process.exit(1);
  }
}

// Run the script
main().catch(error => {
  console.error('Error:', error.message);
  process.exit(1);
});