import React from 'react';
import { render } from '@testing-library/react';
import { MantineProvider } from '@mantine/core';
import { GoogleOAuthProvider } from '@react-oauth/google';
import { UserContext } from './contexts/UserContext';
import { BrowserRouter } from 'react-router-dom';
import { Notifications } from '@mantine/notifications';

// Default test theme matching the app theme
const testTheme = {
  primaryColor: 'blue',
  colors: {
    blue: ['#e7f5ff', '#d0ebff', '#a5d8ff', '#74c0fc', '#4dabf7', '#339af0', '#228be6', '#1c7ed6', '#1971c2', '#1864ab'],
  },
  fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
  headings: { fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif' },
  defaultRadius: 'md',
};

// Mock user data
export const mockUser = {
  email: 'test@example.com',
  name: 'Test User',
  picture: 'https://example.com/avatar.jpg',
  exp: Math.floor(Date.now() / 1000) + 3600, // 1 hour from now
};

// Mock JWT token
export const mockToken = 'mock-jwt-token';

// Custom render function with all providers
export const renderWithProviders = (
  ui,
  {
    user = null,
    token = null,
    ...renderOptions
  } = {}
) => {
  const AllTheProviders = ({ children }) => {
    return (
      <GoogleOAuthProvider clientId="test-client-id">
        <MantineProvider theme={testTheme}>
          <Notifications position="top-right" />
          <UserContext.Provider value={{ user, token }}>
            <BrowserRouter>
              {children}
            </BrowserRouter>
          </UserContext.Provider>
        </MantineProvider>
      </GoogleOAuthProvider>
    );
  };

  return render(ui, { wrapper: AllTheProviders, ...renderOptions });
};

// Re-export everything from React Testing Library
export * from '@testing-library/react';

// Helper to wait for async operations
export const waitForAsync = () => new Promise(resolve => setTimeout(resolve, 0));

// Mock progress data
export const mockProgressData = [
  {
    id: '1',
    clientKrn: 'krn:clnt:demo-company',
    filename: 'sales-data.csv',
    email: 'test@example.com',
    counts: { done: 850, warn: 25, failed: 5 },
    total: 1000,
    isCompleted: false,
    createdAt: '2024-01-15T09:00:00Z',
    updatedAt: '2024-01-15T09:45:00Z',
    errors: [
      { 
        message: 'Date format validation failed',
        pattern: 'Invalid date format', 
        lines: [{ line: 45, values: ['2024-13-45', '2023-02-30'] }]
      }
    ],
    warnings: [
      { 
        message: 'Using deprecated field name',
        pattern: 'Deprecated field', 
        lines: [{ line: 23, values: ['old_field_name'] }]
      }
    ]
  },
  {
    id: '2',
    clientKrn: 'krn:clnt:demo-company',
    filename: 'customer-import.csv',
    email: 'test@example.com',
    counts: { done: 500, warn: 0, failed: 0 },
    total: 500,
    isCompleted: true,
    createdAt: '2024-01-15T08:00:00Z',
    updatedAt: '2024-01-15T08:30:00Z',
    errors: [],
    warnings: []
  }
]; 