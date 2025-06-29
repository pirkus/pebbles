import React, { useState, useEffect, useContext } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Container,
  Card,
  Text,
  Group,
  Stack,
  Badge,
  Button,
  Title,
  Progress,
  Loader,
  Center,
  Alert,
  Table,
  Tabs,
  RingProgress,
  Grid,
  Breadcrumbs,
  Anchor,
  Code
} from '@mantine/core';
import { 
  IconAlertTriangle, 
  IconCircleCheck, 
  IconInfoCircle, 
  IconArrowLeft,
  IconRefresh,
  IconFileAnalytics,
  IconClock,
  IconUser
} from '@tabler/icons-react';
import { notifications } from '@mantine/notifications';
import { UserContext } from '../contexts/UserContext';

const ProgressDetails = () => {
  const { clientKrn, filename } = useParams();
  const navigate = useNavigate();
  const { token } = useContext(UserContext);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [progressData, setProgressData] = useState(null);
  const [lastUpdate, setLastUpdate] = useState(new Date());
  const [expandedErrors, setExpandedErrors] = useState({});
  const [expandedWarnings, setExpandedWarnings] = useState({});

  const fetchProgressDetails = async () => {
    try {
      const response = await fetch(`/progress/${clientKrn}?filename=${encodeURIComponent(filename)}`, {
        headers: {
          'Authorization': `Bearer ${token}`
        }
      });

      if (!response.ok) {
        if (response.status === 404) {
          throw new Error('Progress data not found for this file');
        }
        throw new Error('Failed to fetch progress details');
      }

      const data = await response.json();
      setProgressData(data);
      setLastUpdate(new Date());
      setError(null);
    } catch (err) {
      setError(err.message);
      notifications.show({
        title: 'Error',
        message: err.message,
        color: 'red',
        icon: <IconAlertTriangle />
      });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchProgressDetails();
    
    // Set up polling for live updates every second
    const interval = setInterval(fetchProgressDetails, 1000);
    
    return () => clearInterval(interval);
  }, [clientKrn, filename, token]);

  const formatDate = (dateString) => {
    return new Date(dateString).toLocaleString();
  };

  const getProgressPercentage = () => {
    if (!progressData?.total) return 0;
    const processed = (progressData.counts?.done || 0) + 
                     (progressData.counts?.warn || 0) + 
                     (progressData.counts?.failed || 0);
    return Math.round((processed / progressData.total) * 100);
  };

  const getProgressColor = () => {
    if (progressData?.counts?.failed > 0) return 'red';
    if (progressData?.counts?.warn > 0) return 'yellow';
    if (progressData?.isCompleted) return 'green';
    return 'blue';
  };

  const getStatusIcon = () => {
    if (progressData?.counts?.failed > 0) return <IconAlertTriangle size={20} />;
    if (progressData?.counts?.warn > 0) return <IconInfoCircle size={20} />;
    if (progressData?.isCompleted) return <IconCircleCheck size={20} />;
    return <IconClock size={20} />;
  };

  if (loading && !progressData) {
    return (
      <Center h={400}>
        <Loader size="lg" />
      </Center>
    );
  }

  if (error && !progressData) {
    return (
      <Container size="lg">
        <Alert
          icon={<IconAlertTriangle />}
          title="Error"
          color="red"
          mb="lg"
        >
          {error}
        </Alert>
        <Button
          leftSection={<IconArrowLeft size={16} />}
          variant="light"
          onClick={() => navigate('/progress')}
        >
          Back to Progress List
        </Button>
      </Container>
    );
  }

  return (
    <Container size="lg">
      <Breadcrumbs mb="md">
        <Anchor component="button" onClick={() => navigate('/')}>Dashboard</Anchor>
        <Anchor component="button" onClick={() => navigate('/progress')}>Progress List</Anchor>
        <Text>{filename}</Text>
      </Breadcrumbs>

      <Group justify="space-between" mb="xl">
        <Group>
          <Button
            leftSection={<IconArrowLeft size={16} />}
            variant="subtle"
            onClick={() => navigate('/progress')}
          >
            Back
          </Button>
          <Title order={2}>{progressData?.filename}</Title>
        </Group>
        <Group>
          <Text size="sm" c="dimmed">
            Last updated: {lastUpdate.toLocaleTimeString()}
          </Text>
          <Button
            leftSection={<IconRefresh size={16} />}
            variant="light"
            onClick={fetchProgressDetails}
            loading={loading}
          >
            Refresh
          </Button>
        </Group>
      </Group>

      {progressData && (
        <>
          <Grid mb="xl">
            <Grid.Col span={{ base: 12, md: 6 }}>
              <Card shadow="sm" padding="lg" h="100%">
                <Stack>
                  <Group justify="space-between">
                    <Text size="lg" fw={600}>Overall Progress</Text>
                    {getStatusIcon()}
                  </Group>
                  
                  <Center>
                    <RingProgress
                      size={180}
                      thickness={16}
                      sections={[
                        { 
                          value: getProgressPercentage(), 
                          color: getProgressColor(),
                          tooltip: `${getProgressPercentage()}% Complete`
                        }
                      ]}
                      label={
                        <Center>
                          <Stack gap={0}>
                            <Text size="xl" fw={700}>{getProgressPercentage()}%</Text>
                            <Text size="xs" c="dimmed">Complete</Text>
                          </Stack>
                        </Center>
                      }
                    />
                  </Center>

                  <Progress
                    value={getProgressPercentage()}
                    color={getProgressColor()}
                    size="xl"
                    radius="md"
                  />

                  <Group justify="space-between">
                    <Text size="sm" c="dimmed">
                      {(progressData.counts?.done || 0) + 
                       (progressData.counts?.warn || 0) + 
                       (progressData.counts?.failed || 0)} of {progressData.total || 'Unknown'} items
                    </Text>
                    <Badge
                      color={progressData.isCompleted ? 'green' : 'orange'}
                      variant="light"
                      size="lg"
                    >
                      {progressData.isCompleted ? 'Completed' : 'In Progress'}
                    </Badge>
                  </Group>
                </Stack>
              </Card>
            </Grid.Col>

            <Grid.Col span={{ base: 12, md: 6 }}>
              <Card shadow="sm" padding="lg" h="100%">
                <Stack>
                  <Text size="lg" fw={600}>Details</Text>
                  
                  <Stack gap="xs">
                    <Group justify="space-between">
                      <Text size="sm" c="dimmed">File:</Text>
                      <Code>{progressData.filename}</Code>
                    </Group>
                    
                    <Group justify="space-between">
                      <Text size="sm" c="dimmed">User:</Text>
                      <Group gap="xs">
                        <IconUser size={16} />
                        <Text size="sm">{progressData.email}</Text>
                      </Group>
                    </Group>
                    
                    <Group justify="space-between">
                      <Text size="sm" c="dimmed">Started:</Text>
                      <Text size="sm">{formatDate(progressData.createdAt)}</Text>
                    </Group>
                    
                    <Group justify="space-between">
                      <Text size="sm" c="dimmed">Last Update:</Text>
                      <Text size="sm">{formatDate(progressData.updatedAt)}</Text>
                    </Group>
                  </Stack>
                </Stack>
              </Card>
            </Grid.Col>
          </Grid>

          <Card shadow="sm" padding="lg" mb="xl">
            <Text size="lg" fw={600} mb="md">Processing Statistics</Text>
            <Grid>
              <Grid.Col span={{ base: 12, sm: 4 }}>
                <Card padding="md" withBorder>
                  <Stack gap="xs" align="center">
                    <IconCircleCheck size={32} color="var(--mantine-color-green-6)" />
                    <Text size="xl" fw={700} c="green">{progressData.counts?.done || 0}</Text>
                    <Text size="sm" c="dimmed">Successful</Text>
                  </Stack>
                </Card>
              </Grid.Col>
              
              <Grid.Col span={{ base: 12, sm: 4 }}>
                <Card padding="md" withBorder>
                  <Stack gap="xs" align="center">
                    <IconInfoCircle size={32} color="var(--mantine-color-yellow-6)" />
                    <Text size="xl" fw={700} c="yellow">{progressData.counts?.warn || 0}</Text>
                    <Text size="sm" c="dimmed">Warnings</Text>
                  </Stack>
                </Card>
              </Grid.Col>
              
              <Grid.Col span={{ base: 12, sm: 4 }}>
                <Card padding="md" withBorder>
                  <Stack gap="xs" align="center">
                    <IconAlertTriangle size={32} color="var(--mantine-color-red-6)" />
                    <Text size="xl" fw={700} c="red">{progressData.counts?.failed || 0}</Text>
                    <Text size="sm" c="dimmed">Errors</Text>
                  </Stack>
                </Card>
              </Grid.Col>
            </Grid>
          </Card>

          {(progressData.errors?.length > 0 || progressData.warnings?.length > 0) && (
            <Card shadow="sm" padding="lg">
              <Tabs defaultValue="errors">
                <Tabs.List>
                  <Tabs.Tab 
                    value="errors" 
                    leftSection={<IconAlertTriangle size={16} />}
                    disabled={!progressData.errors?.length}
                  >
                    Errors ({progressData.errors?.length || 0})
                  </Tabs.Tab>
                  <Tabs.Tab 
                    value="warnings" 
                    leftSection={<IconInfoCircle size={16} />}
                    disabled={!progressData.warnings?.length}
                  >
                    Warnings ({progressData.warnings?.length || 0})
                  </Tabs.Tab>
                </Tabs.List>

                <Tabs.Panel value="errors" pt="md">
                  {progressData.errors?.length > 0 ? (
                    <Table.ScrollContainer minWidth={600}>
                      <Table striped>
                        <Table.Thead>
                          <Table.Tr>
                            <Table.Th>Message</Table.Th>
                            <Table.Th>Pattern</Table.Th>
                            <Table.Th>Occurrences</Table.Th>
                            <Table.Th>Lines</Table.Th>
                            <Table.Th>Values</Table.Th>
                          </Table.Tr>
                        </Table.Thead>
                        <Table.Tbody>
                          {progressData.errors.flatMap((error, errorIndex) => {
                            if (error.lines?.length > 0) {
                              const isExpanded = expandedErrors[errorIndex];
                              const hasMore = error.lines.length > 1;
                              
                              if (isExpanded) {
                                // Show all lines when expanded
                                const rows = error.lines.map((line, lineIndex) => (
                                  <Table.Tr key={`${errorIndex}-${lineIndex}`}>
                                    <Table.Td>
                                      {lineIndex === 0 && (
                                        <Text size="sm" c="red" fw={500}>
                                          {error.message || 'N/A'}
                                        </Text>
                                      )}
                                    </Table.Td>
                                    <Table.Td>
                                      {lineIndex === 0 && (
                                        <Text size="sm" c="red">
                                          {error.pattern || 'N/A'}
                                        </Text>
                                      )}
                                    </Table.Td>
                                    <Table.Td>
                                      {lineIndex === 0 && (
                                        <Badge color="red" variant="light">
                                          {error.lines.length}
                                        </Badge>
                                      )}
                                    </Table.Td>
                                    <Table.Td>
                                      <Text size="sm">
                                        {line.line || 'N/A'}
                                      </Text>
                                    </Table.Td>
                                    <Table.Td>
                                      <Text size="sm">
                                        {line.values?.length > 0 ? line.values.join(', ') : 'N/A'}
                                      </Text>
                                    </Table.Td>
                                  </Table.Tr>
                                ));
                                
                                rows.push(
                                  <Table.Tr key={`${errorIndex}-collapse`} style={{ cursor: 'pointer' }} onClick={() => setExpandedErrors(prev => ({ ...prev, [errorIndex]: false }))}>
                                    <Table.Td></Table.Td>
                                    <Table.Td></Table.Td>
                                    <Table.Td></Table.Td>
                                    <Table.Td>
                                      <Text size="sm" c="dimmed" style={{ cursor: 'pointer' }}>
                                        (click to collapse)
                                      </Text>
                                    </Table.Td>
                                    <Table.Td></Table.Td>
                                  </Table.Tr>
                                );
                                
                                return rows;
                              } else {
                                // Show compact single row when collapsed
                                return [
                                  <Table.Tr key={errorIndex} style={{ cursor: hasMore ? 'pointer' : 'default' }} onClick={hasMore ? () => setExpandedErrors(prev => ({ ...prev, [errorIndex]: true })) : undefined}>
                                    <Table.Td>
                                      <Text size="sm" c="red" fw={500}>
                                        {error.message || 'N/A'}
                                      </Text>
                                    </Table.Td>
                                    <Table.Td>
                                      <Text size="sm" c="red">
                                        {error.pattern || 'N/A'}
                                      </Text>
                                    </Table.Td>
                                    <Table.Td>
                                      <Badge color="red" variant="light">
                                        {error.lines.length}
                                      </Badge>
                                    </Table.Td>
                                    <Table.Td>
                                      <Text size="sm">
                                        {error.lines[0]?.line || 'N/A'}
                                        {hasMore && (
                                          <Text component="span" size="sm" c="dimmed" ml="xs">
                                            + {error.lines.length - 1} more lines (click to expand)
                                          </Text>
                                        )}
                                      </Text>
                                    </Table.Td>
                                    <Table.Td>
                                      <Text size="sm">
                                        {hasMore ? 'Multiple' : (error.lines[0]?.values?.length > 0 ? error.lines[0].values.join(', ') : 'N/A')}
                                      </Text>
                                    </Table.Td>
                                  </Table.Tr>
                                ];
                              }
                            } else {
                              return [
                                <Table.Tr key={errorIndex}>
                                  <Table.Td>
                                    <Text size="sm" c="red" fw={500}>
                                      {error.message || 'N/A'}
                                    </Text>
                                  </Table.Td>
                                  <Table.Td>
                                    <Text size="sm" c="red">
                                      {error.pattern || 'N/A'}
                                    </Text>
                                  </Table.Td>
                                  <Table.Td>
                                    <Badge color="red" variant="light">
                                      {error.line ? 1 : 0}
                                    </Badge>
                                  </Table.Td>
                                  <Table.Td>
                                    <Text size="sm">
                                      {error.line || 'N/A'}
                                    </Text>
                                  </Table.Td>
                                  <Table.Td>
                                    <Text size="sm">N/A</Text>
                                  </Table.Td>
                                </Table.Tr>
                              ];
                            }
                          })}
                        </Table.Tbody>
                      </Table>
                    </Table.ScrollContainer>
                  ) : (
                    <Text c="dimmed" ta="center" py="md">No errors</Text>
                  )}
                </Tabs.Panel>

                <Tabs.Panel value="warnings" pt="md">
                  {progressData.warnings?.length > 0 ? (
                    <Table.ScrollContainer minWidth={600}>
                      <Table striped>
                        <Table.Thead>
                          <Table.Tr>
                            <Table.Th>Message</Table.Th>
                            <Table.Th>Pattern</Table.Th>
                            <Table.Th>Occurrences</Table.Th>
                            <Table.Th>Lines</Table.Th>
                            <Table.Th>Values</Table.Th>
                          </Table.Tr>
                        </Table.Thead>
                        <Table.Tbody>
                          {progressData.warnings.flatMap((warning, warningIndex) => {
                            if (warning.lines?.length > 0) {
                              const isExpanded = expandedWarnings[warningIndex];
                              const hasMore = warning.lines.length > 1;
                              
                              if (isExpanded) {
                                // Show all lines when expanded
                                const rows = warning.lines.map((line, lineIndex) => (
                                  <Table.Tr key={`${warningIndex}-${lineIndex}`}>
                                    <Table.Td>
                                      {lineIndex === 0 && (
                                        <Text size="sm" c="yellow" fw={500}>
                                          {warning.message || 'N/A'}
                                        </Text>
                                      )}
                                    </Table.Td>
                                    <Table.Td>
                                      {lineIndex === 0 && (
                                        <Text size="sm" c="yellow">
                                          {warning.pattern || 'N/A'}
                                        </Text>
                                      )}
                                    </Table.Td>
                                    <Table.Td>
                                      {lineIndex === 0 && (
                                        <Badge color="yellow" variant="light">
                                          {warning.lines.length}
                                        </Badge>
                                      )}
                                    </Table.Td>
                                    <Table.Td>
                                      <Text size="sm">
                                        {line.line || 'N/A'}
                                      </Text>
                                    </Table.Td>
                                    <Table.Td>
                                      <Text size="sm">
                                        {line.values?.length > 0 ? line.values.join(', ') : 'N/A'}
                                      </Text>
                                    </Table.Td>
                                  </Table.Tr>
                                ));
                                
                                rows.push(
                                  <Table.Tr key={`${warningIndex}-collapse`} style={{ cursor: 'pointer' }} onClick={() => setExpandedWarnings(prev => ({ ...prev, [warningIndex]: false }))}>
                                    <Table.Td></Table.Td>
                                    <Table.Td></Table.Td>
                                    <Table.Td></Table.Td>
                                    <Table.Td>
                                      <Text size="sm" c="dimmed" style={{ cursor: 'pointer' }}>
                                        (click to collapse)
                                      </Text>
                                    </Table.Td>
                                    <Table.Td></Table.Td>
                                  </Table.Tr>
                                );
                                
                                return rows;
                              } else {
                                // Show compact single row when collapsed
                                return [
                                  <Table.Tr key={warningIndex} style={{ cursor: hasMore ? 'pointer' : 'default' }} onClick={hasMore ? () => setExpandedWarnings(prev => ({ ...prev, [warningIndex]: true })) : undefined}>
                                    <Table.Td>
                                      <Text size="sm" c="yellow" fw={500}>
                                        {warning.message || 'N/A'}
                                      </Text>
                                    </Table.Td>
                                    <Table.Td>
                                      <Text size="sm" c="yellow">
                                        {warning.pattern || 'N/A'}
                                      </Text>
                                    </Table.Td>
                                    <Table.Td>
                                      <Badge color="yellow" variant="light">
                                        {warning.lines.length}
                                      </Badge>
                                    </Table.Td>
                                    <Table.Td>
                                      <Text size="sm">
                                        {warning.lines[0]?.line || 'N/A'}
                                        {hasMore && (
                                          <Text component="span" size="sm" c="dimmed" ml="xs">
                                            + {warning.lines.length - 1} more lines (click to expand)
                                          </Text>
                                        )}
                                      </Text>
                                    </Table.Td>
                                    <Table.Td>
                                      <Text size="sm">
                                        {hasMore ? 'Multiple' : (warning.lines[0]?.values?.length > 0 ? warning.lines[0].values.join(', ') : 'N/A')}
                                      </Text>
                                    </Table.Td>
                                  </Table.Tr>
                                ];
                              }
                            } else {
                              return [
                                <Table.Tr key={warningIndex}>
                                  <Table.Td>
                                    <Text size="sm" c="yellow" fw={500}>
                                      {warning.message || 'N/A'}
                                    </Text>
                                  </Table.Td>
                                  <Table.Td>
                                    <Text size="sm" c="yellow">
                                      {warning.pattern || 'N/A'}
                                    </Text>
                                  </Table.Td>
                                  <Table.Td>
                                    <Badge color="yellow" variant="light">
                                      {warning.line ? 1 : 0}
                                    </Badge>
                                  </Table.Td>
                                  <Table.Td>
                                    <Text size="sm">
                                      {warning.line || 'N/A'}
                                    </Text>
                                  </Table.Td>
                                  <Table.Td>
                                    <Text size="sm">N/A</Text>
                                  </Table.Td>
                                </Table.Tr>
                              ];
                            }
                          })}
                        </Table.Tbody>
                      </Table>
                    </Table.ScrollContainer>
                  ) : (
                    <Text c="dimmed" ta="center" py="md">No warnings</Text>
                  )}
                </Tabs.Panel>
              </Tabs>
            </Card>
          )}
        </>
      )}
    </Container>
  );
};

export default ProgressDetails; 