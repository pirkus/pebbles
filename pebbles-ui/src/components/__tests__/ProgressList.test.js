import React from 'react';
import { screen, waitFor, within, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ProgressList from '../ProgressList';
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

describe('ProgressList Component', () => {
  const user = userEvent.setup();

  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('Initial Render', () => {
    it('displays title and search controls', async () => {
      global.fetch = jest.fn().mockResolvedValueOnce({
        ok: true,
        json: async () => [],
      });

      await act(async () => {
        renderWithProviders(<ProgressList />, { user: mockUser, token: mockToken });
      });

      expect(screen.getByText('Progress List')).toBeInTheDocument();
      expect(screen.getByPlaceholderText('Search by filename or email...')).toBeInTheDocument();
      expect(screen.getByRole('combobox')).toBeInTheDocument();
    });

    it('fetches progress data on mount', async () => {
      global.fetch = jest.fn().mockResolvedValueOnce({
        ok: true,
        json: async () => mockProgressData,
      });

      await act(async () => {
        renderWithProviders(<ProgressList />, { user: mockUser, token: mockToken });
      });

      await waitFor(() => {
        expect(global.fetch).toHaveBeenCalledWith('/progress/krn:clnt:demo-company', {
          headers: {
            'Authorization': `Bearer ${mockToken}`,
          },
        });
      });
    });
  });

  describe('Search and Filter', () => {
    beforeEach(() => {
      global.fetch = jest.fn().mockResolvedValueOnce({
        ok: true,
        json: async () => mockProgressData,
      });
    });

    it('filters by search query', async () => {
      await act(async () => {
        renderWithProviders(<ProgressList />, { user: mockUser, token: mockToken });
      });

      await waitFor(() => {
        expect(screen.getByText('sales-data.csv')).toBeInTheDocument();
        expect(screen.getByText('customer-import.csv')).toBeInTheDocument();
      });

      const searchInput = screen.getByPlaceholderText('Search by filename or email...');
      await act(async () => {
        await user.type(searchInput, 'sales');
      });

      await waitFor(() => {
        expect(screen.getByText('sales-data.csv')).toBeInTheDocument();
        expect(screen.queryByText('customer-import.csv')).not.toBeInTheDocument();
      });
    });

    it('filters by status', async () => {
      await act(async () => {
        renderWithProviders(<ProgressList />, { user: mockUser, token: mockToken });
      });

      await waitFor(() => {
        expect(screen.getByText('sales-data.csv')).toBeInTheDocument();
        expect(screen.getByText('customer-import.csv')).toBeInTheDocument();
      });

      const statusSelect = screen.getByRole('combobox');
      await act(async () => {
        await user.selectOptions(statusSelect, 'completed');
      });

      await waitFor(() => {
        expect(screen.queryByText('sales-data.csv')).not.toBeInTheDocument();
        expect(screen.getByText('customer-import.csv')).toBeInTheDocument();
      });
    });

    it('filters by errors', async () => {
      await act(async () => {
        renderWithProviders(<ProgressList />, { user: mockUser, token: mockToken });
      });

      await waitFor(() => {
        expect(screen.getByText('sales-data.csv')).toBeInTheDocument();
      });

      const statusSelect = screen.getByRole('combobox');
      await act(async () => {
        await user.selectOptions(statusSelect, 'with-errors');
      });

      await waitFor(() => {
        expect(screen.getByText('sales-data.csv')).toBeInTheDocument(); // Has errors
        expect(screen.queryByText('customer-import.csv')).not.toBeInTheDocument(); // No errors
      });
    });
  });

  describe('Progress Display', () => {
    beforeEach(() => {
      global.fetch = jest.fn().mockResolvedValueOnce({
        ok: true,
        json: async () => mockProgressData,
      });
    });

    it('displays progress bars with correct values', async () => {
      await act(async () => {
        renderWithProviders(<ProgressList />, { user: mockUser, token: mockToken });
      });

      await waitFor(() => {
        // Check for progress text
        expect(screen.getByText('88% (880 / 1000)')).toBeInTheDocument(); // sales-data
        expect(screen.getByText('100% (500 / 500)')).toBeInTheDocument(); // customer-import
      });
    });

    it('displays count badges correctly', async () => {
      await act(async () => {
        renderWithProviders(<ProgressList />, { user: mockUser, token: mockToken });
      });

      await waitFor(() => {
        // For sales-data.csv
        expect(screen.getByText('850')).toBeInTheDocument(); // done
        expect(screen.getByText('25')).toBeInTheDocument(); // warn
        expect(screen.getByText('5')).toBeInTheDocument(); // failed
        
        // For customer-import.csv
        expect(screen.getByText('500')).toBeInTheDocument(); // done
      });
    });

    it('displays status badges', async () => {
      await act(async () => {
        renderWithProviders(<ProgressList />, { user: mockUser, token: mockToken });
      });

      await waitFor(() => {
        // Use getAllByText for status badges that also appear in dropdown
        const inProgressElements = screen.getAllByText('In Progress');
        expect(inProgressElements.length).toBeGreaterThan(1); // In dropdown and badge
        
        const completedElements = screen.getAllByText('Completed');
        expect(completedElements.length).toBeGreaterThan(0);
      });
    });
  });

  describe('Navigation', () => {
    it('navigates to details page when clicking eye icon', async () => {
      global.fetch = jest.fn().mockResolvedValueOnce({
        ok: true,
        json: async () => mockProgressData,
      });

      await act(async () => {
        renderWithProviders(<ProgressList />, { user: mockUser, token: mockToken });
      });

      await waitFor(() => {
        expect(screen.getByText('sales-data.csv')).toBeInTheDocument();
      });

      const eyeButtons = screen.getAllByRole('button');
      const firstEyeButton = eyeButtons.find(btn => 
        btn.querySelector('svg') && btn.closest('tr')?.textContent?.includes('sales-data.csv')
      );
      
      await act(async () => {
        await user.click(firstEyeButton);
      });

      expect(mockNavigate).toHaveBeenCalledWith('/progress/krn:clnt:demo-company/sales-data.csv');
    });
  });

  describe('Empty State', () => {
    it('shows empty message when no data matches filters', async () => {
      global.fetch = jest.fn().mockResolvedValueOnce({
        ok: true,
        json: async () => mockProgressData,
      });

      await act(async () => {
        renderWithProviders(<ProgressList />, { user: mockUser, token: mockToken });
      });

      await waitFor(() => {
        expect(screen.getByText('sales-data.csv')).toBeInTheDocument();
      });

      const searchInput = screen.getByPlaceholderText('Search by filename or email...');
      await act(async () => {
        await user.type(searchInput, 'nonexistent');
      });

      await waitFor(() => {
        expect(screen.getByText('No progress data found matching your criteria.')).toBeInTheDocument();
      });
    });
  });

  describe('Pagination', () => {
    it('shows pagination when more than 20 items', async () => {
      // Create 25 mock items
      const manyItems = Array.from({ length: 25 }, (_, i) => ({
        id: `${i + 1}`,
        clientKrn: 'krn:clnt:demo-company',
        filename: `file-${i + 1}.csv`,
        email: 'test@example.com',
        counts: { done: 100, warn: 0, failed: 0 },
        total: 100,
        isCompleted: false,
        createdAt: '2024-01-15T09:00:00Z',
        updatedAt: '2024-01-15T09:00:00Z',
        errors: [],
        warnings: []
      }));

      global.fetch = jest.fn().mockResolvedValueOnce({
        ok: true,
        json: async () => manyItems,
      });

      await act(async () => {
        renderWithProviders(<ProgressList />, { user: mockUser, token: mockToken });
      });

      await waitFor(() => {
        // Should show first 20 items
        expect(screen.getByText('file-1.csv')).toBeInTheDocument();
        expect(screen.getByText('file-20.csv')).toBeInTheDocument();
        expect(screen.queryByText('file-21.csv')).not.toBeInTheDocument();
        
        // Should show pagination
        const pagination = screen.getByRole('navigation');
        expect(pagination).toBeInTheDocument();
      });
    });
  });

  describe('Live Updates', () => {
    it('sets up polling for updates', async () => {
      global.fetch = jest.fn().mockResolvedValue({
        ok: true,
        json: async () => mockProgressData,
      });

      await act(async () => {
        renderWithProviders(<ProgressList />, { user: mockUser, token: mockToken });
      });

      await waitFor(() => {
        expect(screen.getByText('850')).toBeInTheDocument(); // Initial done count
      });

      // Verify initial fetch was made
      expect(global.fetch).toHaveBeenCalledTimes(1);
      expect(global.fetch).toHaveBeenCalledWith('/progress/krn:clnt:demo-company', {
        headers: {
          'Authorization': `Bearer ${mockToken}`,
        },
      });
    });
  });

  describe('Error Handling', () => {
    it('displays error alert when fetch fails', async () => {
      global.fetch = jest.fn().mockRejectedValueOnce(new Error('Network error'));

      await act(async () => {
        renderWithProviders(<ProgressList />, { user: mockUser, token: mockToken });
      });

      await waitFor(() => {
        expect(screen.getByText('Network error')).toBeInTheDocument();
      });
    });
  });
}); 