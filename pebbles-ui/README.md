# Pebbles UI

A real-time progress tracking UI for the Pebbles project built with React and Mantine components.

## Features

- **Live Progress Updates**: Real-time updates every second for active file processing
- **Dashboard Overview**: Quick statistics and recent activity view
- **Detailed Progress View**: In-depth progress tracking with error and warning details
- **Filterable List**: Search and filter progress by status, filename, or user
- **Google OAuth**: Secure authentication using Google login
- **Responsive Design**: Works seamlessly on desktop and mobile devices

## Prerequisites

- Node.js (v14 or higher)
- npm or yarn
- Pebbles backend running on http://localhost:8081

## Installation

1. Navigate to the pebbles-ui directory:
```bash
cd pebbles/pebbles-ui
```

2. Install dependencies:
```bash
npm install
```

## Configuration

### Backend Proxy
The application is configured to proxy API requests to `http://localhost:8081` (as defined in package.json).

### Client KRN
Currently using a demo client KRN (`krn:clnt:demo-company`). Update this in the components:
- `ProgressDashboard.js`
- `ProgressList.js`

### Google OAuth
The app uses the same Google OAuth client ID as horti-ui. To use your own:
1. Update the client ID in `App.js`
2. Ensure your Google Cloud Console has the appropriate redirect URIs configured

## Running the Application

1. Start the Pebbles backend server (should be running on port 8081)

2. Start the UI development server:
```bash
npm start
```

3. Open [http://localhost:3000](http://localhost:3000) in your browser

## Available Scripts

- `npm start` - Runs the app in development mode
- `npm build` - Builds the app for production
- `npm test` - Runs the test suite
- `npm test -- --coverage` - Runs tests with coverage report
- `npm eject` - Ejects from Create React App (one-way operation)

## Project Structure

```
pebbles-ui/
├── public/
│   ├── index.html
│   └── manifest.json
├── src/
│   ├── components/
│   │   ├── __tests__/                    # Component tests
│   │   │   ├── Login.test.js
│   │   │   ├── ProgressDashboard.test.js
│   │   │   ├── ProgressList.test.js
│   │   │   ├── ProgressDetails.test.js
│   │   │   └── MantineIntegration.test.js
│   │   ├── Login.js                      # Google OAuth login
│   │   ├── ProgressDashboard.js          # Main dashboard with statistics
│   │   ├── ProgressList.js               # Filterable list of all progress
│   │   └── ProgressDetails.js            # Detailed view with live updates
│   ├── contexts/
│   │   └── UserContext.js                # Authentication context
│   ├── __mocks__/                        # Test mocks
│   │   ├── @react-oauth/
│   │   │   └── google.js
│   │   └── react-router-dom.js
│   ├── App.js                            # Main app with routing
│   ├── index.js                          # Entry point
│   ├── index.css                         # Global styles
│   ├── reportWebVitals.js                # Performance monitoring
│   ├── setupTests.js                     # Jest setup
│   └── test-utils.js                     # Testing utilities
├── jest.config.js                        # Jest configuration
├── package.json
└── README.md
```

## Usage

1. **Login**: Use your Google account to authenticate
2. **Dashboard**: View overall statistics and recent activity
3. **Progress List**: Browse all progress items with search and filtering
4. **Progress Details**: Click on any item to see real-time detailed progress

## Testing

The project includes comprehensive test coverage using Jest and React Testing Library.

### Running Tests

```bash
# Run all tests
npm test

# Run tests with coverage report
npm test -- --coverage

# Run tests in watch mode
npm test -- --watch

# Run a specific test file
npm test Login.test.js
```

### Test Structure

- **Component Tests**: Each component has a corresponding test file in `__tests__/`
- **Integration Tests**: `MantineIntegration.test.js` tests Mantine component integration
- **Test Utilities**: `test-utils.js` provides helper functions and mock data
- **Mocks**: External dependencies are mocked in `__mocks__/`

### Coverage Requirements

The project is configured with the following coverage thresholds:
- Branches: 70%
- Functions: 70%
- Lines: 70%
- Statements: 70%

## Future Enhancements

- Configurable client KRN
- User preferences and settings
- Export functionality for progress reports
- WebSocket support for more efficient real-time updates
- Dark mode support

## Troubleshooting

- **API Connection Issues**: Ensure the Pebbles backend is running on port 8081
- **Authentication Errors**: Check that your Google OAuth token is valid
- **CORS Issues**: The backend should be configured to accept requests from localhost:3000

## License

Same as the parent Pebbles project. 