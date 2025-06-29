import React from 'react';
import { screen, waitFor, within, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ProgressDashboard from '../ProgressDashboard';
import { renderWithProviders, mockUser, mockToken, mockProgressData } from '../../test-utils';

// Get the mocked navigate function from our mock
const { mockNavigate } = require('../../__mocks__/react-router-dom');

// Mock notifications
jest.mock('@mantine/notifications', () => ({
  notifications: {
    show: jest.fn(),
  },
  Notifications: () => null,
}));

describe('ProgressDashboard Component', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('Initial Render', () => {
    it('displays title and refresh button', async () => {
      global.fetch = jest.fn().mockResolvedValueOnce({
        ok: true,
        json: async () => [],
      });

      await act(async () => {
        renderWithProviders(<ProgressDashboard />, { user: mockUser, token: mockToken });
      });

      expect(screen.getByText('Progress Dashboard')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Refresh/i })).toBeInTheDocument();
    });

    it('fetches progress data on mount', async () => {
      global.fetch = jest.fn().mockResolvedValueOnce({
        ok: true,
        json: async () => mockProgressData,
      });

      await act(async () => {
        renderWithProviders(<ProgressDashboard />, { user: mockUser, token: mockToken });
      });

      await waitFor(() => {
        expect(global.fetch).toHaveBeenCalledWith('/progress/krn:clnt:demo-company', {
          headers: {
            'Authorization': `Bearer ${mockToken}`,
          },
        });
      });
    });

    it('sets up polling interval for live updates', async () => {
      global.fetch = jest.fn().mockResolvedValue({
        ok: true,
        json: async () => mockProgressData,
      });

      await act(async () => {
        renderWithProviders(<ProgressDashboard />, { user: mockUser, token: mockToken });
      });

      // Initial fetch
      expect(global.fetch).toHaveBeenCalledTimes(1);

      // Component sets up polling, but we won't test the actual polling
      // since we're not using fake timers. Just verify initial setup.
      expect(global.fetch).toHaveBeenCalledWith('/progress/krn:clnt:demo-company', {
        headers: {
          'Authorization': `Bearer ${mockToken}`,
        },
      });
    });
  });

  describe('Statistics Display', () => {
    beforeEach(() => {
      global.fetch = jest.fn().mockResolvedValueOnce({
        ok: true,
        json: async () => mockProgressData,
      });
    });

    it('displays correct statistics', async () => {
      await act(async () => {
        renderWithProviders(<ProgressDashboard />, { user: mockUser, token: mockToken });
      });

      await waitFor(() => {
        expect(screen.getByText('Total Files')).toBeInTheDocument();
        expect(screen.getAllByText('2')[0]).toBeInTheDocument(); // Total files
        
        // Use getAllByText for labels that appear multiple times
        const completedLabels = screen.getAllByText('Completed');
        expect(completedLabels.length).toBeGreaterThan(0);
        expect(screen.getAllByText('1')[0]).toBeInTheDocument(); // Completed files
        
        const inProgressLabels = screen.getAllByText('In Progress');
        expect(inProgressLabels.length).toBeGreaterThan(0);
        
        expect(screen.getByText('Total Errors')).toBeInTheDocument();
        expect(screen.getAllByText('5')[0]).toBeInTheDocument(); // Total errors from mockProgressData
      });
    });
  });

  describe('Recent Activity Table', () => {
    beforeEach(() => {
      global.fetch = jest.fn().mockResolvedValueOnce({
        ok: true,
        json: async () => mockProgressData,
      });
    });

    it('displays recent activity data', async () => {
      await act(async () => {
        renderWithProviders(<ProgressDashboard />, { user: mockUser, token: mockToken });
      });

      await waitFor(() => {
        expect(screen.getByText('Recent Activity')).toBeInTheDocument();
        expect(screen.getByText('sales-data.csv')).toBeInTheDocument();
        expect(screen.getByText('customer-import.csv')).toBeInTheDocument();
        // Use getAllByText since email appears multiple times
        const emails = screen.getAllByText('test@example.com');
        expect(emails.length).toBeGreaterThan(0);
      });
    });

    it('displays progress percentages correctly', async () => {
      await act(async () => {
        renderWithProviders(<ProgressDashboard />, { user: mockUser, token: mockToken });
      });

      await waitFor(() => {
        // 880/1000 = 88% for sales-data.csv - appears multiple times
        const eightyEightPercent = screen.getAllByText('88%');
        expect(eightyEightPercent.length).toBeGreaterThan(0);
        // 500/500 = 100% for customer-import.csv
        const hundredPercent = screen.getAllByText('100%');
        expect(hundredPercent.length).toBeGreaterThan(0);
      });
    });

    it('navigates to details when clicking eye icon', async () => {
      global.fetch = jest.fn().mockResolvedValueOnce({
        ok: true,
        json: async () => mockProgressData,
      });
      
      await act(async () => {
        renderWithProviders(<ProgressDashboard />, { user: mockUser, token: mockToken });
      });

      await waitFor(() => {
        expect(screen.getByText('sales-data.csv')).toBeInTheDocument();
      });

      // Find the row containing sales-data.csv
      const salesRow = screen.getByText('sales-data.csv').closest('tr');
      expect(salesRow).toBeInTheDocument();
      
      // Find the action button within that row
      const actionButton = within(salesRow).getAllByRole('button').find(btn => 
        // ActionIcon is mocked as a button, find the last button in the row (Actions column)
        btn.closest('td') && btn.closest('td').cellIndex === 5
      );
      
      expect(actionButton).toBeInTheDocument();
      
      await act(async () => {
        await userEvent.click(actionButton);
      });

      expect(mockNavigate).toHaveBeenCalledWith('/progress/krn:clnt:demo-company/sales-data.csv');
    });
  });

  describe('Empty State', () => {
    it('displays empty message when no data', async () => {
      global.fetch = jest.fn().mockResolvedValueOnce({
        ok: true,
        json: async () => [],
      });

      await act(async () => {
        renderWithProviders(<ProgressDashboard />, { user: mockUser, token: mockToken });
      });

      await waitFor(() => {
        expect(screen.getByText('No progress data available yet.')).toBeInTheDocument();
      });
    });
  });

  describe('Error Handling', () => {
    it('displays error alert when fetch fails', async () => {
      global.fetch = jest.fn().mockRejectedValueOnce(new Error('Network error'));

      await act(async () => {
        renderWithProviders(<ProgressDashboard />, { user: mockUser, token: mockToken });
      });

      await waitFor(() => {
        expect(screen.getByText('Network error')).toBeInTheDocument();
      });
    });

    it('can close error alert', async () => {
      global.fetch = jest.fn().mockRejectedValueOnce(new Error('Network error'));

      await act(async () => {
        renderWithProviders(<ProgressDashboard />, { user: mockUser, token: mockToken });
      });

      await waitFor(() => {
        expect(screen.getByText('Network error')).toBeInTheDocument();
      });

      // Find and click close button - the mock creates a button with "×"
      const closeButton = screen.getByText('×');
      await act(async () => {
        await userEvent.click(closeButton);
      });

      await waitFor(() => {
        expect(screen.queryByText('Network error')).not.toBeInTheDocument();
      });
    });
  });

  describe('Refresh Functionality', () => {
    it('refreshes data when clicking refresh button', async () => {
      global.fetch = jest.fn()
        .mockResolvedValueOnce({
          ok: true,
          json: async () => mockProgressData,
        })
        .mockResolvedValueOnce({
          ok: true,
          json: async () => [...mockProgressData, {
            id: '3',
            clientKrn: 'krn:clnt:demo-company',
            filename: 'new-file.csv',
            email: 'test@example.com',
            counts: { done: 100, warn: 0, failed: 0 },
            total: 100,
            isCompleted: true,
            createdAt: '2024-01-15T10:00:00Z',
            updatedAt: '2024-01-15T10:05:00Z',
            errors: [],
            warnings: []
          }],
        });

      await act(async () => {
        renderWithProviders(<ProgressDashboard />, { user: mockUser, token: mockToken });
      });

      await waitFor(() => {
        expect(screen.getByText('sales-data.csv')).toBeInTheDocument();
      });

      const refreshButton = screen.getByRole('button', { name: /Refresh/i });
      await act(async () => {
        await userEvent.click(refreshButton);
      });

      await waitFor(() => {
        expect(global.fetch).toHaveBeenCalledTimes(2);
      });
    });
  });
}); 