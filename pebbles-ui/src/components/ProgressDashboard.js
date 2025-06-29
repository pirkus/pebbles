import React, { useState, useEffect, useContext } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Container,
  Grid,
  Card,
  Text,
  Group,
  Stack,
  RingProgress,
  Badge,
  Button,
  Title,
  Table,
  ActionIcon,
  Loader,
  Center,
  Alert
} from '@mantine/core';
import { IconFileAnalytics, IconAlertTriangle, IconCircleCheck, IconInfoCircle, IconRefresh, IconEye } from '@tabler/icons-react';
import { notifications } from '@mantine/notifications';
import { UserContext } from '../contexts/UserContext';

const ProgressDashboard = () => {
  const navigate = useNavigate();
  const { token } = useContext(UserContext);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [progressData, setProgressData] = useState([]);
  const [stats, setStats] = useState({
    totalFiles: 0,
    completed: 0,
    inProgress: 0,
    totalProcessed: 0,
    totalWarnings: 0,
    totalErrors: 0
  });

  // TODO: Replace with actual client KRN from configuration or user context
  const clientKrn = 'krn:clnt:demo-company';

  const fetchProgressData = async () => {
    try {
      const response = await fetch(`/progress/${clientKrn}`, {
        headers: {
          'Authorization': `Bearer ${token}`
        }
      });

      if (!response.ok) {
        throw new Error('Failed to fetch progress data');
      }

      const data = await response.json();
      setProgressData(data);
      
      // Calculate statistics
      const stats = data.reduce((acc, item) => {
        acc.totalFiles++;
        if (item.isCompleted) {
          acc.completed++;
        } else {
          acc.inProgress++;
        }
        acc.totalProcessed += (item.counts?.done || 0);
        acc.totalWarnings += (item.counts?.warn || 0);
        acc.totalErrors += (item.counts?.failed || 0);
        return acc;
      }, {
        totalFiles: 0,
        completed: 0,
        inProgress: 0,
        totalProcessed: 0,
        totalWarnings: 0,
        totalErrors: 0
      });
      
      setStats(stats);
      setError(null);
    } catch (err) {
      setError(err.message);
      notifications.show({
        title: 'Error',
        message: 'Failed to fetch progress data',
        color: 'red',
        icon: <IconAlertTriangle />
      });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchProgressData();
    
    // Set up polling for live updates
    const interval = setInterval(fetchProgressData, 1000); // Update every second
    
    return () => clearInterval(interval);
  }, [token]);

  const formatDate = (dateString) => {
    return new Date(dateString).toLocaleString();
  };

  const getProgressPercentage = (item) => {
    if (!item.total) return 0;
    const processed = (item.counts?.done || 0) + (item.counts?.warn || 0) + (item.counts?.failed || 0);
    return Math.round((processed / item.total) * 100);
  };

  if (loading && progressData.length === 0) {
    return (
      <Center h={400}>
        <Loader size="lg" />
      </Center>
    );
  }

  return (
    <Container size="xl">
      <Group justify="space-between" mb="xl">
        <Title order={2}>Progress Dashboard</Title>
        <Button
          leftSection={<IconRefresh size={16} />}
          variant="light"
          onClick={fetchProgressData}
          loading={loading}
        >
          Refresh
        </Button>
      </Group>

      {error && (
        <Alert
          icon={<IconAlertTriangle />}
          title="Error"
          color="red"
          mb="lg"
          withCloseButton
          onClose={() => setError(null)}
        >
          {error}
        </Alert>
      )}

      <Grid mb="xl">
        <Grid.Col span={{ base: 12, sm: 6, md: 3 }}>
          <Card padding="lg" shadow="sm">
            <Stack gap="xs">
              <Group justify="space-between">
                <Text size="sm" c="dimmed">Total Files</Text>
                <IconFileAnalytics size={20} color="var(--mantine-color-blue-6)" />
              </Group>
              <Text size="xl" fw={700}>{stats.totalFiles}</Text>
            </Stack>
          </Card>
        </Grid.Col>

        <Grid.Col span={{ base: 12, sm: 6, md: 3 }}>
          <Card padding="lg" shadow="sm">
            <Stack gap="xs">
              <Group justify="space-between">
                <Text size="sm" c="dimmed">Completed</Text>
                <IconCircleCheck size={20} color="var(--mantine-color-green-6)" />
              </Group>
              <Text size="xl" fw={700} c="green">{stats.completed}</Text>
            </Stack>
          </Card>
        </Grid.Col>

        <Grid.Col span={{ base: 12, sm: 6, md: 3 }}>
          <Card padding="lg" shadow="sm">
            <Stack gap="xs">
              <Group justify="space-between">
                <Text size="sm" c="dimmed">In Progress</Text>
                <IconInfoCircle size={20} color="var(--mantine-color-orange-6)" />
              </Group>
              <Text size="xl" fw={700} c="orange">{stats.inProgress}</Text>
            </Stack>
          </Card>
        </Grid.Col>

        <Grid.Col span={{ base: 12, sm: 6, md: 3 }}>
          <Card padding="lg" shadow="sm">
            <Stack gap="xs">
              <Group justify="space-between">
                <Text size="sm" c="dimmed">Total Errors</Text>
                <IconAlertTriangle size={20} color="var(--mantine-color-red-6)" />
              </Group>
              <Text size="xl" fw={700} c="red">{stats.totalErrors}</Text>
            </Stack>
          </Card>
        </Grid.Col>
      </Grid>

      <Card shadow="sm" padding="lg">
        <Title order={4} mb="md">Recent Activity</Title>
        {progressData.length === 0 ? (
          <Text c="dimmed" ta="center" py="xl">
            No progress data available yet.
          </Text>
        ) : (
          <Table.ScrollContainer minWidth={600}>
            <Table striped highlightOnHover>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Filename</Table.Th>
                  <Table.Th>User</Table.Th>
                  <Table.Th>Progress</Table.Th>
                  <Table.Th>Status</Table.Th>
                  <Table.Th>Last Updated</Table.Th>
                  <Table.Th>Actions</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {progressData.slice(0, 10).map((item) => (
                  <Table.Tr key={item.id}>
                    <Table.Td>
                      <Text fw={500}>{item.filename}</Text>
                    </Table.Td>
                    <Table.Td>
                      <Text size="sm" c="dimmed">{item.email}</Text>
                    </Table.Td>
                    <Table.Td>
                      <Group gap="xs">
                        <RingProgress
                          size={40}
                          thickness={4}
                          sections={[
                            { value: getProgressPercentage(item), color: 'blue' }
                          ]}
                        />
                        <Text size="sm">
                          {getProgressPercentage(item)}%
                        </Text>
                      </Group>
                    </Table.Td>
                    <Table.Td>
                      <Badge
                        color={item.isCompleted ? 'green' : 'orange'}
                        variant="light"
                      >
                        {item.isCompleted ? 'Completed' : 'In Progress'}
                      </Badge>
                    </Table.Td>
                    <Table.Td>
                      <Text size="sm">{formatDate(item.updatedAt)}</Text>
                    </Table.Td>
                    <Table.Td>
                      <ActionIcon
                        variant="subtle"
                        onClick={() => navigate(`/progress/${item.clientKrn}/${item.filename}`)}
                      >
                        <IconEye size={16} />
                      </ActionIcon>
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
        )}
      </Card>
    </Container>
  );
};

export default ProgressDashboard; 