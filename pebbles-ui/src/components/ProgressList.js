import React, { useState, useEffect, useContext } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Container,
  Card,
  Text,
  Group,
  Stack,
  Badge,
  Button,
  Title,
  Table,
  ActionIcon,
  Loader,
  Center,
  Alert,
  TextInput,
  Select,
  Progress,
  Tooltip,
  Pagination
} from '@mantine/core';
import { IconSearch, IconEye, IconAlertTriangle, IconCircleCheck, IconInfoCircle, IconClock } from '@tabler/icons-react';
import { notifications } from '@mantine/notifications';
import { UserContext } from '../contexts/UserContext';

const ProgressList = () => {
  const navigate = useNavigate();
  const { token } = useContext(UserContext);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [progressData, setProgressData] = useState([]);
  const [filteredData, setFilteredData] = useState([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');
  const [currentPage, setCurrentPage] = useState(1);
  const itemsPerPage = 20;

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
      setFilteredData(data);
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
    const interval = setInterval(fetchProgressData, 5000); // Update every 5 seconds
    
    return () => clearInterval(interval);
  }, [token]);

  useEffect(() => {
    let filtered = [...progressData];
    
    // Apply search filter
    if (searchQuery) {
      filtered = filtered.filter(item =>
        item.filename.toLowerCase().includes(searchQuery.toLowerCase()) ||
        item.email.toLowerCase().includes(searchQuery.toLowerCase())
      );
    }
    
    // Apply status filter
    if (statusFilter !== 'all') {
      filtered = filtered.filter(item => {
        if (statusFilter === 'completed') return item.isCompleted;
        if (statusFilter === 'in-progress') return !item.isCompleted;
        if (statusFilter === 'with-errors') return item.counts?.failed > 0;
        if (statusFilter === 'with-warnings') return item.counts?.warn > 0;
        return true;
      });
    }
    
    setFilteredData(filtered);
    setCurrentPage(1);
  }, [searchQuery, statusFilter, progressData]);

  const formatDate = (dateString) => {
    return new Date(dateString).toLocaleString();
  };

  const getProgressPercentage = (item) => {
    if (!item.total) return 0;
    const processed = (item.counts?.done || 0) + (item.counts?.warn || 0) + (item.counts?.failed || 0);
    return Math.round((processed / item.total) * 100);
  };

  const getProgressColor = (item) => {
    if (item.counts?.failed > 0) return 'red';
    if (item.counts?.warn > 0) return 'yellow';
    if (item.isCompleted) return 'green';
    return 'blue';
  };

  const paginatedData = filteredData.slice(
    (currentPage - 1) * itemsPerPage,
    currentPage * itemsPerPage
  );

  if (loading && progressData.length === 0) {
    return (
      <Center h={400}>
        <Loader size="lg" />
      </Center>
    );
  }

  return (
    <Container size="xl">
      <Title order={2} mb="xl">Progress List</Title>

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

      <Card shadow="sm" padding="lg" mb="lg">
        <Group>
          <TextInput
            placeholder="Search by filename or email..."
            leftSection={<IconSearch size={16} />}
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            style={{ flex: 1 }}
          />
          <Select
            placeholder="Filter by status"
            value={statusFilter}
            onChange={setStatusFilter}
            data={[
              { value: 'all', label: 'All Status' },
              { value: 'completed', label: 'Completed' },
              { value: 'in-progress', label: 'In Progress' },
              { value: 'with-errors', label: 'With Errors' },
              { value: 'with-warnings', label: 'With Warnings' }
            ]}
            w={200}
          />
        </Group>
      </Card>

      <Card shadow="sm" padding="lg">
        <Text size="sm" c="dimmed" mb="md">
          Showing {paginatedData.length} of {filteredData.length} items
        </Text>

        {paginatedData.length === 0 ? (
          <Text c="dimmed" ta="center" py="xl">
            No progress data found matching your criteria.
          </Text>
        ) : (
          <>
            <Table.ScrollContainer minWidth={800}>
              <Table striped highlightOnHover>
                <Table.Thead>
                  <Table.Tr>
                    <Table.Th>Filename</Table.Th>
                    <Table.Th>User</Table.Th>
                    <Table.Th>Progress</Table.Th>
                    <Table.Th>Counts</Table.Th>
                    <Table.Th>Status</Table.Th>
                    <Table.Th>Created</Table.Th>
                    <Table.Th>Last Updated</Table.Th>
                    <Table.Th>Actions</Table.Th>
                  </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  {paginatedData.map((item) => (
                    <Table.Tr key={item.id}>
                      <Table.Td>
                        <Text fw={500}>{item.filename}</Text>
                      </Table.Td>
                      <Table.Td>
                        <Text size="sm" c="dimmed">{item.email}</Text>
                      </Table.Td>
                      <Table.Td>
                        <Stack gap="xs">
                          <Progress
                            value={getProgressPercentage(item)}
                            color={getProgressColor(item)}
                            size="md"
                          />
                          <Text size="xs" ta="center">
                            {getProgressPercentage(item)}% 
                            {item.total && ` (${(item.counts?.done || 0) + (item.counts?.warn || 0) + (item.counts?.failed || 0)} / ${item.total})`}
                          </Text>
                        </Stack>
                      </Table.Td>
                      <Table.Td>
                        <Stack gap={4}>
                          <Group gap="xs">
                            <Tooltip label="Successful">
                              <Badge
                                leftSection={<IconCircleCheck size={12} />}
                                color="green"
                                variant="light"
                                size="sm"
                              >
                                {item.counts?.done || 0}
                              </Badge>
                            </Tooltip>
                            <Tooltip label="Warnings">
                              <Badge
                                leftSection={<IconInfoCircle size={12} />}
                                color="yellow"
                                variant="light"
                                size="sm"
                              >
                                {item.counts?.warn || 0}
                              </Badge>
                            </Tooltip>
                            <Tooltip label="Errors">
                              <Badge
                                leftSection={<IconAlertTriangle size={12} />}
                                color="red"
                                variant="light"
                                size="sm"
                              >
                                {item.counts?.failed || 0}
                              </Badge>
                            </Tooltip>
                          </Group>
                        </Stack>
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
                        <Tooltip label={formatDate(item.createdAt)}>
                          <Group gap={4}>
                            <IconClock size={14} />
                            <Text size="sm">{new Date(item.createdAt).toLocaleDateString()}</Text>
                          </Group>
                        </Tooltip>
                      </Table.Td>
                      <Table.Td>
                        <Tooltip label={formatDate(item.updatedAt)}>
                          <Group gap={4}>
                            <IconClock size={14} />
                            <Text size="sm">{new Date(item.updatedAt).toLocaleDateString()}</Text>
                          </Group>
                        </Tooltip>
                      </Table.Td>
                      <Table.Td>
                        <Tooltip label="View Details">
                          <ActionIcon
                            variant="subtle"
                            onClick={() => navigate(`/progress/${item.clientKrn}/${item.filename}`)}
                          >
                            <IconEye size={16} />
                          </ActionIcon>
                        </Tooltip>
                      </Table.Td>
                    </Table.Tr>
                  ))}
                </Table.Tbody>
              </Table>
            </Table.ScrollContainer>
            
            {filteredData.length > itemsPerPage && (
              <Center mt="lg">
                <Pagination
                  total={Math.ceil(filteredData.length / itemsPerPage)}
                  value={currentPage}
                  onChange={setCurrentPage}
                />
              </Center>
            )}
          </>
        )}
      </Card>
    </Container>
  );
};

export default ProgressList; 