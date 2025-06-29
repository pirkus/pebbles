// Mock all Tabler icons as simple SVG elements
const createIcon = (name) => {
  const Icon = (props) => {
    // Add color class if color prop includes 'blue'
    const colorClass = props.color && props.color.includes('blue') ? 'icon-blue' : '';
    return (
      <svg 
        className={`tabler-icon tabler-icon-${name} ${colorClass} ${props.className || ''}`}
        width={props.size || 24}
        height={props.size || 24}
        viewBox="0 0 24 24"
        stroke="currentColor"
        strokeWidth="2"
        fill="none"
        strokeLinecap="round"
        strokeLinejoin="round"
        {...props}
      >
        <path d={`M${name}`} />
      </svg>
    );
  };
  Icon.displayName = name;
  return Icon;
};

// Export all icons used in the app
export const IconProgress = createIcon('progress');
export const IconList = createIcon('list');
export const IconDashboard = createIcon('dashboard');
export const IconSettings = createIcon('settings');
export const IconLogout = createIcon('logout');
export const IconFileAnalytics = createIcon('file-analytics');
export const IconAlertTriangle = createIcon('alert-triangle');
export const IconCircleCheck = createIcon('circle-check');
export const IconInfoCircle = createIcon('info-circle');
export const IconRefresh = createIcon('refresh');
export const IconEye = createIcon('eye');
export const IconSearch = createIcon('search');
export const IconClock = createIcon('clock');
export const IconArrowLeft = createIcon('arrow-left');
export const IconUser = createIcon('user'); 