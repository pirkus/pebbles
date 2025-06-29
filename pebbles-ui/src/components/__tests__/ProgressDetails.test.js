import React from 'react';
import { screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ProgressDetails from '../ProgressDetails';
import { renderWithProviders, mockUser, mockToken, mockProgressData } from '../../test-utils';

// Get the mocked navigate function and params from our mock
const { mockNavigate, mockParams } = require('../../__mocks__/react-router-dom');

// Mock notifications
jest.mock('@mantine/notifications', () => ({
  notifications: {
    show: jest.fn(),
  },
  Notifications: () => null,
}));

const mockDetailedProgress = {
  ...mockProgressData[0],
  errors: [
    { 
      pattern: 'Invalid date format', 
      lines: [
        { line: 45, values: ['2024-13-45'] },
        { line: 67, values: ['2023-02-30'] },
        { line: 123, values: ['invalid-date'] }
      ]
    },
    {
      pattern: 'Missing required field',
      lines: [
        { line: 89, values: ['email', 'phone'] }
      ]
    }
  ],
  warnings: [
    {
      pattern: 'Deprecated field used',
      lines: [
        { line: 23, values: ['old_field_name'] },
        { line: 56, values: ['legacy_column'] }
      ]
    }
  ]
};

describe('ProgressDetails Component', () => {
  const user = userEvent.setup();

  beforeEach(() => {
    jest.clearAllMocks();
    // Set up the route params
    mockParams.clientKrn = 'krn:clnt:demo-company';
    mockParams.filename = 'sales-data.csv';
  });

  describe('Initial Render', () => {
    it('displays loading state initially', async () => {
      global.fetch = jest.fn().mockImplementation(() => 
        new Promise(resolve => setTimeout(() => resolve({
          ok: true,
          json: async () => mockDetailedProgress,
        }), 100))
      );

      const { container } = renderWithProviders(<ProgressDetails />, { user: mockUser, token: mockToken });

      expect(container.querySelector('.mantine-Loader-root')).toBeInTheDocument(); // Loader
    });

    it('fetches progress details on mount', async () => {
      global.fetch = jest.fn().mockResolvedValueOnce({
        ok: true,
        json: async () => mockDetailedProgress,
      });

      await act(async () => {
        renderWithProviders(<ProgressDetails />, { user: mockUser, token: mockToken });
      });

      await waitFor(() => {
        expect(global.fetch).toHaveBeenCalledWith(
          '/progress/krn:clnt:demo-company?filename=sales-data.csv',
          {
            headers: {
              'Authorization': `Bearer ${mockToken}`,
            },
          }
        );
      });
    });

    it('displays breadcrumbs and navigation', async () => {
      global.fetch = jest.fn().mockResolvedValueOnce({
        ok: true,
        json: async () => mockDetailedProgress,
      });

      await act(async () => {
        renderWithProviders(<ProgressDetails />, { user: mockUser, token: mockToken });
      });

      await waitFor(() => {
        expect(screen.getByText('Dashboard')).toBeInTheDocument();
        expect(screen.getByText('Progress List')).toBeInTheDocument();
        // Use getAllByText since filename appears multiple times
        const filenames = screen.getAllByText('sales-data.csv');
        expect(filenames.length).toBeGreaterThan(0);
        expect(screen.getByRole('button', { name: /Back/i })).toBeInTheDocument();
      });
    });
  });

  describe('Progress Display', () => {
    beforeEach(() => {
      global.fetch = jest.fn().mockResolvedValueOnce({
        ok: true,
        json: async () => mockDetailedProgress,
      });
    });

    it('displays overall progress correctly', async () => {
      await act(async () => {
        renderWithProviders(<ProgressDetails />, { user: mockUser, token: mockToken });
      });

      await waitFor(() => {
        expect(screen.getByText('Overall Progress')).toBeInTheDocument();
        expect(screen.getByText('88%')).toBeInTheDocument(); // Progress percentage
        expect(screen.getByText('880 of 1000 items')).toBeInTheDocument();
        expect(screen.getByText('In Progress')).toBeInTheDocument();
      });
    });

    it('displays file details', async () => {
      await act(async () => {
        renderWithProviders(<ProgressDetails />, { user: mockUser, token: mockToken });
      });

      await waitFor(() => {
        expect(screen.getByText('Details')).toBeInTheDocument();
        // Use getAllByText since filename appears multiple times
        const filenames = screen.getAllByText('sales-data.csv');
        expect(filenames.length).toBeGreaterThan(0);
        expect(screen.getByText('test@example.com')).toBeInTheDocument();
      });
    });

    it('displays processing statistics', async () => {
      await act(async () => {
        renderWithProviders(<ProgressDetails />, { user: mockUser, token: mockToken });
      });

      await waitFor(() => {
        expect(screen.getByText('Processing Statistics')).toBeInTheDocument();
        expect(screen.getByText('850')).toBeInTheDocument(); // Successful
        expect(screen.getByText('25')).toBeInTheDocument(); // Warnings
        expect(screen.getByText('5')).toBeInTheDocument(); // Errors
        expect(screen.getByText('Successful')).toBeInTheDocument();
        expect(screen.getByText('Warnings')).toBeInTheDocument();
        expect(screen.getByText('Errors')).toBeInTheDocument();
      });
    });
  });

  describe('Errors and Warnings', () => {
    it('displays errors tab with details', async () => {
      global.fetch = jest.fn().mockResolvedValueOnce({
        ok: true,
        json: async () => mockDetailedProgress,
      });

      await act(async () => {
        renderWithProviders(<ProgressDetails />, { user: mockUser, token: mockToken });
      });

      await waitFor(() => {
        expect(screen.getByText('Errors (2)')).toBeInTheDocument();
        // Check for patterns  
        expect(screen.getByText('Invalid date format')).toBeInTheDocument();
        expect(screen.getByText('Missing required field')).toBeInTheDocument();
        // Check that tables display the data (both errors and warnings tables)
        const tables = screen.getAllByRole('table');
        expect(tables.length).toBeGreaterThanOrEqual(1);
        // Check specific values from the error data
        expect(screen.getByText('email, phone')).toBeInTheDocument();
        expect(screen.getByText('3')).toBeInTheDocument(); // Badge count
      });
    });

    it('displays warnings tab with details', async () => {
      global.fetch = jest.fn().mockResolvedValueOnce({
        ok: true,
        json: async () => mockDetailedProgress,
      });

      await act(async () => {
        renderWithProviders(<ProgressDetails />, { user: mockUser, token: mockToken });
      });

      await waitFor(() => {
        expect(screen.getByText('Warnings (1)')).toBeInTheDocument();
      });

      // Click on warnings tab
      const warningsTab = screen.getByText('Warnings (1)');
      await act(async () => {
        await user.click(warningsTab);
      });

      await waitFor(() => {
        // Check for pattern
        expect(screen.getByText('Deprecated field used')).toBeInTheDocument();
        // Check that warning content is displayed
        expect(screen.getByText(/23/)).toBeInTheDocument();
      });
    });

    it('handles no errors or warnings', async () => {
      global.fetch = jest.fn().mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          ...mockDetailedProgress,
          errors: [],
          warnings: []
        }),
      });

      await act(async () => {
        renderWithProviders(<ProgressDetails />, { user: mockUser, token: mockToken });
      });

      await waitFor(() => {
        expect(screen.queryByText(/Errors \(/)).not.toBeInTheDocument();
        expect(screen.queryByText(/Warnings \(/)).not.toBeInTheDocument();
      });
    });
  });

  describe('Live Updates', () => {
    it('sets up polling for updates', async () => {
      global.fetch = jest.fn().mockResolvedValue({
        ok: true,
        json: async () => mockDetailedProgress,
      });

      await act(async () => {
        renderWithProviders(<ProgressDetails />, { user: mockUser, token: mockToken });
      });

      await waitFor(() => {
        expect(screen.getByText('850')).toBeInTheDocument(); // Initial count
      });

      // Verify that the component rendered and initial fetch was made
      expect(global.fetch).toHaveBeenCalledTimes(1);
      expect(global.fetch).toHaveBeenCalledWith(
        '/progress/krn:clnt:demo-company?filename=sales-data.csv',
        expect.any(Object)
      );
    });
  });

  describe('Navigation', () => {
    it('navigates back when clicking back button', async () => {
      global.fetch = jest.fn().mockResolvedValueOnce({
        ok: true,
        json: async () => mockDetailedProgress,
      });

      await act(async () => {
        renderWithProviders(<ProgressDetails />, { user: mockUser, token: mockToken });
      });

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /Back/i })).toBeInTheDocument();
      });

      const backButton = screen.getByRole('button', { name: /Back/i });
      await act(async () => {
        await user.click(backButton);
      });

      expect(mockNavigate).toHaveBeenCalledWith('/progress');
    });

    it('navigates when clicking breadcrumb links', async () => {
      global.fetch = jest.fn().mockResolvedValueOnce({
        ok: true,
        json: async () => mockDetailedProgress,
      });

      await act(async () => {
        renderWithProviders(<ProgressDetails />, { user: mockUser, token: mockToken });
      });

      await waitFor(() => {
        expect(screen.getByText('Dashboard')).toBeInTheDocument();
      });

      const dashboardLink = screen.getByText('Dashboard');
      await act(async () => {
        await user.click(dashboardLink);
      });

      expect(mockNavigate).toHaveBeenCalledWith('/');
    });
  });

  describe('Error States', () => {
    it('displays error when progress not found', async () => {
      global.fetch = jest.fn().mockResolvedValueOnce({
        ok: false,
        status: 404,
      });

      await act(async () => {
        renderWithProviders(<ProgressDetails />, { user: mockUser, token: mockToken });
      });

      await waitFor(() => {
        expect(screen.getByText('Progress data not found for this file')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Back to Progress List/i })).toBeInTheDocument();
      });
    });

    it('displays error when fetch fails', async () => {
      global.fetch = jest.fn().mockRejectedValueOnce(new Error('Network error'));

      await act(async () => {
        renderWithProviders(<ProgressDetails />, { user: mockUser, token: mockToken });
      });

      await waitFor(() => {
        expect(screen.getByText('Network error')).toBeInTheDocument();
      });
    });
  });

  describe('Refresh Functionality', () => {
    it('refreshes data when clicking refresh button', async () => {
      global.fetch = jest.fn()
        .mockResolvedValueOnce({
          ok: true,
          json: async () => mockDetailedProgress,
        })
        .mockResolvedValueOnce({
          ok: true,
          json: async () => ({
            ...mockDetailedProgress,
            counts: { done: 950, warn: 25, failed: 5 },
          }),
        });

      await act(async () => {
        renderWithProviders(<ProgressDetails />, { user: mockUser, token: mockToken });
      });

      await waitFor(() => {
        expect(screen.getByText('850')).toBeInTheDocument();
      });

      const refreshButton = screen.getByRole('button', { name: /Refresh/i });
      await act(async () => {
        await user.click(refreshButton);
      });

      await waitFor(() => {
        expect(global.fetch).toHaveBeenCalledTimes(2);
      });
    });
  });
}); 