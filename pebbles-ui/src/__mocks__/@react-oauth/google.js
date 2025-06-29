import React from 'react';

export const GoogleOAuthProvider = ({ children }) => <div>{children}</div>;

export const GoogleLogin = ({ onSuccess, onError }) => (
  <button
    data-testid="google-login-button"
    onClick={() => onSuccess({ credential: 'mock-credential' })}
  >
    Sign in with Google
  </button>
);

export const useGoogleLogin = () => {
  return () => {};
}; 