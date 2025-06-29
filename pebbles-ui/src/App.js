import React, { useState, useEffect } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate, useNavigate, useLocation } from 'react-router-dom';
import { MantineProvider, AppShell, Container, Group, Text, Button, NavLink, Stack } from '@mantine/core';
import { Notifications } from '@mantine/notifications';

// Import Mantine styles
import '@mantine/core/styles.css';
import '@mantine/notifications/styles.css';
import { GoogleOAuthProvider } from '@react-oauth/google';
import { jwtDecode } from 'jwt-decode';
import { IconProgress, IconList, IconDashboard, IconSettings, IconLogout } from '@tabler/icons-react';

import Login from './components/Login';
import ProgressDashboard from './components/ProgressDashboard';
import ProgressList from './components/ProgressList';
import ProgressDetails from './components/ProgressDetails';
import { UserContext } from './contexts/UserContext';

const theme = {
  primaryColor: 'blue',
  colors: {
    blue: ['#e7f5ff', '#d0ebff', '#a5d8ff', '#74c0fc', '#4dabf7', '#339af0', '#228be6', '#1c7ed6', '#1971c2', '#1864ab'],
  },
  fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
  headings: { fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif' },
  defaultRadius: 'md',
};

const AppHeader = ({ user, handleLogout }) => {
  const navigate = useNavigate();
  
  return (
    <AppShell.Header>
      <Group justify="space-between" h="100%" px="md">
        <Text 
          size={{ base: "lg", sm: "xl" }} 
          fw={600}
          style={{ cursor: 'pointer' }}
          onClick={() => navigate('/')}
        >
          ⚡ Pebbles Progress Tracker
        </Text>
        {user && (
          <Group>
            <Text size="sm" c="dimmed" visibleFrom="sm">
              {user.name || user.email}
            </Text>
            <Button variant="light" onClick={handleLogout} size="sm">
              Logout
            </Button>
          </Group>
        )}
      </Group>
    </AppShell.Header>
  );
};

const AppNavbar = ({ handleLogout }) => {
  const navigate = useNavigate();
  const location = useLocation();
  
  const navItems = [
    { label: 'Dashboard', icon: IconDashboard, path: '/' },
    { label: 'Progress List', icon: IconList, path: '/progress' },
    { label: 'Settings', icon: IconSettings, path: '/settings', disabled: true },
  ];
  
  return (
    <AppShell.Navbar p="md">
      <AppShell.Section grow>
        <Stack gap="xs">
          {navItems.map((item) => (
            <NavLink
              key={item.path}
              label={item.label}
              leftSection={<item.icon size={20} />}
              active={location.pathname === item.path}
              onClick={() => !item.disabled && navigate(item.path)}
              disabled={item.disabled}
              style={{ borderRadius: 'var(--mantine-radius-md)' }}
            />
          ))}
        </Stack>
      </AppShell.Section>
      
      <AppShell.Section>
        <NavLink
          label="Logout"
          leftSection={<IconLogout size={20} />}
          onClick={handleLogout}
          color="red"
          style={{ borderRadius: 'var(--mantine-radius-md)' }}
        />
      </AppShell.Section>
    </AppShell.Navbar>
  );
};

function App() {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(localStorage.getItem('authToken'));

  useEffect(() => {
    if (token) {
      try {
        const decoded = jwtDecode(token);
        const currentTime = Date.now() / 1000;
        
        if (decoded.exp > currentTime) {
          setUser(decoded);
        } else {
          // Token expired
          localStorage.removeItem('authToken');
          setToken(null);
        }
      } catch (error) {
        console.error('Invalid token:', error);
        localStorage.removeItem('authToken');
        setToken(null);
      }
    }
  }, [token]);

  const handleLogin = (googleToken) => {
    localStorage.setItem('authToken', googleToken);
    setToken(googleToken);
  };

  const handleLogout = () => {
    localStorage.removeItem('authToken');
    setToken(null);
    setUser(null);
  };

  return (
    <GoogleOAuthProvider clientId="96361216057-f2bbdvmomo6hqbt5sedmlgbeeud8feg7.apps.googleusercontent.com">
      <MantineProvider theme={theme}>
        <Notifications position="top-right" />
        <UserContext.Provider value={{ user, token }}>
          <Router>
            <AppShell 
              header={{ height: 60 }} 
              navbar={{ width: 250, breakpoint: 'sm' }}
              padding="md"
            >
              <AppHeader user={user} handleLogout={handleLogout} />
              {user && <AppNavbar handleLogout={handleLogout} />}

              <AppShell.Main>
                <Container size="lg">
                  <Routes>
                    <Route 
                      path="/login" 
                      element={!user ? <Login onLogin={handleLogin} /> : <Navigate to="/" />} 
                    />
                    <Route 
                      path="/" 
                      element={user ? <ProgressDashboard /> : <Navigate to="/login" />} 
                    />
                    <Route 
                      path="/progress" 
                      element={user ? <ProgressList /> : <Navigate to="/login" />} 
                    />
                    <Route 
                      path="/progress/:clientKrn/:filename" 
                      element={user ? <ProgressDetails /> : <Navigate to="/login" />} 
                    />
                    <Route 
                      path="/settings" 
                      element={
                        user ? (
                          <Container>
                            <Text size="xl" fw={600} mb="md">Settings</Text>
                            <Text c="dimmed">Coming soon...</Text>
                          </Container>
                        ) : (
                          <Navigate to="/login" />
                        )
                      } 
                    />
                  </Routes>
                </Container>
              </AppShell.Main>
            </AppShell>
          </Router>
        </UserContext.Provider>
      </MantineProvider>
    </GoogleOAuthProvider>
  );
}

export default App; 