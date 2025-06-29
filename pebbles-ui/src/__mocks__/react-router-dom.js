import React from 'react';

// Mock navigate function
export const mockNavigate = jest.fn();

// Mock useParams to return test values
export const mockParams = {};

// Mock all react-router-dom exports
export const useNavigate = () => mockNavigate;
export const useParams = () => mockParams;
export const useLocation = () => ({ pathname: '/' });

export const BrowserRouter = ({ children }) => children;
export const Routes = ({ children }) => children;
export const Route = ({ element }) => element;
export const Navigate = () => null;
export const Link = ({ children, to, ...props }) => (
  <a href={to} {...props}>{children}</a>
);

export const Outlet = () => null;
export const useSearchParams = () => [new URLSearchParams(), jest.fn()];

// For backward compatibility with require()
module.exports = {
  mockNavigate,
  mockParams,
  useNavigate,
  useParams,
  useLocation,
  BrowserRouter,
  Routes,
  Route,
  Navigate,
  Link,
  Outlet,
  useSearchParams,
}; 