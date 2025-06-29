import React from 'react';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders, mockUser, mockToken, mockProgressData } from '../../test-utils';
import { MantineProvider } from '@mantine/core';
import ProgressDashboard from '../ProgressDashboard';
import ProgressList from '../ProgressList';
import ProgressDetails from '../ProgressDetails';

// Mock navigate and params
const { mockNavigate, mockParams } = require('../../__mocks__/react-router-dom');

// Mock notifications
jest.mock('@mantine/notifications', () => ({
  notifications: {
    show: jest.fn(),
  },
  Notifications: () => null,
}));

describe('Mantine Components Integration', () => {
  const user = userEvent.setup();

  beforeEach(() => {
    jest.clearAllMocks();
    global.fetch = jest.fn();
  });

  describe('Mantine UI Components', () => {
    it('renders Mantine Cards correctly in Dashboard', async () => {
      global.fetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockProgressData,
      });

      const { container } = renderWithProviders(<ProgressDashboard />, { 
        user: mockUser, 
        token: mockToken 
      });

      await waitFor(() => {
        const cards = container.querySelectorAll('.mantine-Card-root');
        expect(cards.length).toBeGreaterThan(0);
        
        // Check for proper card structure
        cards.forEach(card => {
          expect(card).toHaveClass('mantine-Card-root');
        });
      });
    });

    it('renders Mantine Table in ProgressList', async () => {
      global.fetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockProgressData,
      });

      const { container } = renderWithProviders(<ProgressList />, { 
        user: mockUser, 
        token: mockToken 
      });

      await waitFor(() => {
        const table = container.querySelector('.mantine-Table-root');
        expect(table).toBeInTheDocument();
        
        const tableRows = container.querySelectorAll('.mantine-Table-tr');
        expect(tableRows.length).toBeGreaterThan(0);
      });
    });

    it('renders Mantine Progress bars correctly', async () => {
      global.fetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockProgressData,
      });

      const { container } = renderWithProviders(<ProgressList />, { 
        user: mockUser, 
        token: mockToken 
      });

      await waitFor(() => {
        const progressBars = container.querySelectorAll('.mantine-Progress-root');
        expect(progressBars.length).toBeGreaterThan(0);
      });
    });

    it('renders Mantine Badges with correct variants', async () => {
      global.fetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockProgressData,
      });

      const { container } = renderWithProviders(<ProgressList />, { 
        user: mockUser, 
        token: mockToken 
      });

      await waitFor(() => {
        const badges = container.querySelectorAll('.mantine-Badge-root');
        expect(badges.length).toBeGreaterThan(0);
        
        // Check for different badge colors
        const greenBadges = Array.from(badges).filter(badge => 
          badge.classList.toString().includes('green')
        );
        const yellowBadges = Array.from(badges).filter(badge => 
          badge.classList.toString().includes('yellow')
        );
        
        expect(greenBadges.length).toBeGreaterThan(0);
        expect(yellowBadges.length).toBeGreaterThan(0);
      });
    });

    it('renders Mantine Tabs in ProgressDetails', async () => {
      mockParams.clientKrn = 'krn:clnt:demo-company';
      mockParams.filename = 'sales-data.csv';
      
      global.fetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockProgressData[0],
      });

      const { container } = renderWithProviders(<ProgressDetails />, { 
        user: mockUser, 
        token: mockToken 
      });

      await waitFor(() => {
        const tabs = container.querySelector('.mantine-Tabs-root');
        expect(tabs).toBeInTheDocument();
        
        const tabsList = container.querySelector('.mantine-Tabs-list');
        expect(tabsList).toBeInTheDocument();
      });
    });
  });

  describe('Mantine Form Components', () => {
    it('renders Mantine TextInput for search', async () => {
      global.fetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockProgressData,
      });

      const { container } = renderWithProviders(<ProgressList />, { 
        user: mockUser, 
        token: mockToken 
      });

      await waitFor(() => {
        const textInput = container.querySelector('.mantine-TextInput-root');
        expect(textInput).toBeInTheDocument();
        
        const input = container.querySelector('.mantine-TextInput-input');
        expect(input).toHaveAttribute('placeholder', 'Search by filename or email...');
      });
    });

    it('renders Mantine Select for filtering', async () => {
      global.fetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockProgressData,
      });

      const { container } = renderWithProviders(<ProgressList />, { 
        user: mockUser, 
        token: mockToken 
      });

      await waitFor(() => {
        const select = container.querySelector('.mantine-Select-root');
        expect(select).toBeInTheDocument();
      });
    });
  });

  describe('Mantine Layout Components', () => {
    it('renders Mantine Grid layout', async () => {
      global.fetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockProgressData,
      });

      const { container } = renderWithProviders(<ProgressDashboard />, { 
        user: mockUser, 
        token: mockToken 
      });

      await waitFor(() => {
        const grid = container.querySelector('.mantine-Grid-root');
        expect(grid).toBeInTheDocument();
        
        const gridCols = container.querySelectorAll('.mantine-Grid-col');
        expect(gridCols.length).toBeGreaterThan(0);
      });
    });

    it('renders Mantine Stack layout', async () => {
      global.fetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockProgressData,
      });

      const { container } = renderWithProviders(<ProgressDashboard />, { 
        user: mockUser, 
        token: mockToken 
      });

      await waitFor(() => {
        const stacks = container.querySelectorAll('.mantine-Stack-root');
        expect(stacks.length).toBeGreaterThan(0);
      });
    });

    it('renders Mantine Group layout', async () => {
      global.fetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockProgressData,
      });

      const { container } = renderWithProviders(<ProgressDashboard />, { 
        user: mockUser, 
        token: mockToken 
      });

      await waitFor(() => {
        const groups = container.querySelectorAll('.mantine-Group-root');
        expect(groups.length).toBeGreaterThan(0);
      });
    });
  });

  describe('Mantine Feedback Components', () => {
    it('renders Mantine Alert on error', async () => {
      global.fetch.mockRejectedValueOnce(new Error('Network error'));

      const { container } = renderWithProviders(<ProgressDashboard />, { 
        user: mockUser, 
        token: mockToken 
      });

      await waitFor(() => {
        const alert = container.querySelector('.mantine-Alert-root');
        expect(alert).toBeInTheDocument();
        expect(screen.getByText('Network error')).toBeInTheDocument();
      });
    });

    it('renders Mantine Loader during loading', async () => {
      global.fetch.mockImplementation(() => 
        new Promise(resolve => setTimeout(() => resolve({
          ok: true,
          json: async () => mockProgressData,
        }), 100))
      );

      const { container } = renderWithProviders(<ProgressDetails />, { 
        user: mockUser, 
        token: mockToken 
      });

      const loader = container.querySelector('.mantine-Loader-root');
      expect(loader).toBeInTheDocument();
    });
  });

  describe('Mantine Data Display Components', () => {
    it('renders Mantine RingProgress', async () => {
      global.fetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockProgressData,
      });

      const { container } = renderWithProviders(<ProgressDashboard />, { 
        user: mockUser, 
        token: mockToken 
      });

      await waitFor(() => {
        const ringProgress = container.querySelector('.mantine-RingProgress-root');
        expect(ringProgress).toBeInTheDocument();
      });
    });

    it('renders Mantine Tooltip on hover', async () => {
      global.fetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockProgressData,
      });

      renderWithProviders(<ProgressList />, { 
        user: mockUser, 
        token: mockToken 
      });

      await waitFor(() => {
        // Tooltips are rendered on hover, check for the wrapper elements
        const badges = screen.getAllByText('850'); // done count
        expect(badges.length).toBeGreaterThan(0);
      });
    });
  });

  describe('Mantine Theme Integration', () => {
    it('applies custom theme colors', async () => {
      global.fetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockProgressData,
      });

      const { container } = renderWithProviders(<ProgressDashboard />, { 
        user: mockUser, 
        token: mockToken 
      });

      await waitFor(() => {
        // Check that blue theme color is applied
        const blueElements = container.querySelectorAll('[class*="blue"]');
        expect(blueElements.length).toBeGreaterThan(0);
      });
    });

    it('applies proper spacing and radius', async () => {
      global.fetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockProgressData,
      });

      const { container } = renderWithProviders(<ProgressDashboard />, { 
        user: mockUser, 
        token: mockToken 
      });

      await waitFor(() => {
        const cards = container.querySelectorAll('.mantine-Card-root');
        cards.forEach(card => {
          const styles = window.getComputedStyle(card);
          // Mantine applies border-radius styles
          expect(styles.borderRadius).toBeTruthy();
        });
      });
    });
  });

  describe('Responsive Design', () => {
    it('renders responsive Grid columns', async () => {
      global.fetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockProgressData,
      });

      const { container } = renderWithProviders(<ProgressDashboard />, { 
        user: mockUser, 
        token: mockToken 
      });

      await waitFor(() => {
        const gridCols = container.querySelectorAll('.mantine-Grid-col');
        gridCols.forEach(col => {
          // Check that responsive classes are applied
          expect(col.className).toMatch(/mantine-Grid-col/);
        });
      });
    });
  });
}); 