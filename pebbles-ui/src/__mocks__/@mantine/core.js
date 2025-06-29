import React from 'react';

// Mock all Mantine components as simple divs with appropriate class names
const createMockComponent = (displayName, defaultTag = 'div') => {
  const Component = React.forwardRef(({ children, className = '', ...props }, ref) => {
    const Tag = defaultTag;
    return (
      <Tag 
        ref={ref}
        className={`mantine-${displayName}-root ${className}`}
        {...props}
      >
        {children}
      </Tag>
    );
  });
  Component.displayName = displayName;
  return Component;
};

// Basic components
export const Container = createMockComponent('Container');
export const Grid = createMockComponent('Grid');
export const Card = createMockComponent('Card');
export const Text = createMockComponent('Text', 'span');
export const Group = createMockComponent('Group');
export const Stack = createMockComponent('Stack');
export const Paper = createMockComponent('Paper');
export const Box = createMockComponent('Box');
export const RingProgress = ({ sections, label, ...props }) => {
  const value = sections?.[0]?.value || 0;
  return (
    <div className="mantine-RingProgress-root" {...props}>
      {label && React.isValidElement(label) ? label : <span>{value}%</span>}
    </div>
  );
};
export const Badge = ({ children, leftSection, color, variant, ...props }) => {
  const colorClass = color ? `mantine-Badge-${color}` : '';
  const variantClass = variant ? `mantine-Badge-${variant}` : '';
  return (
    <div className={`mantine-Badge-root ${colorClass} ${variantClass}`} {...props}>
      {leftSection && React.isValidElement(leftSection) && <span>{leftSection}</span>}
      {children}
    </div>
  );
};
export const Button = ({ leftSection, loading, children, ...props }) => (
  <button className="mantine-Button-root" disabled={loading} {...props}>
    {leftSection && React.isValidElement(leftSection) && <span>{leftSection}</span>}
    {loading && <span>Loading...</span>}
    {children}
  </button>
);
export const Title = createMockComponent('Title', 'h2');
export const Table = ({ striped, highlightOnHover, children, ...props }) => (
  <table className="mantine-Table-root" {...props}>
    {children}
  </table>
);
export const ActionIcon = createMockComponent('ActionIcon', 'button');
export const Loader = createMockComponent('Loader');
export const Center = createMockComponent('Center');
export const Alert = ({ children, title, icon, withCloseButton, onClose, ...props }) => (
  <div className="mantine-Alert-root" {...props}>
    {icon && React.isValidElement(icon) && <span>{icon}</span>}
    {title && <div className="mantine-Alert-title">{title}</div>}
    {children}
    {withCloseButton && (
      <button className="mantine-Alert-closeButton" onClick={onClose}>×</button>
    )}
  </div>
);
export const AppShell = createMockComponent('AppShell');
export const NavLink = createMockComponent('NavLink', 'a');
export const Anchor = createMockComponent('Anchor', 'a');
export const Breadcrumbs = createMockComponent('Breadcrumbs', 'nav');
export const Code = createMockComponent('Code', 'code');
export const Progress = createMockComponent('Progress');
export const Tabs = createMockComponent('Tabs');
export const TextInput = ({ leftSection, placeholder, value, onChange, ...props }) => (
  <div className="mantine-TextInput-root" {...props}>
    {leftSection && React.isValidElement(leftSection) && <span>{leftSection}</span>}
    <input 
      className="mantine-TextInput-input" 
      type="text" 
      placeholder={placeholder}
      value={value}
      onChange={onChange}
    />
  </div>
);
export const Select = ({ data, placeholder, value, onChange, ...props }) => {
  const handleChange = (e) => {
    if (onChange) {
      onChange(e.target.value);
    }
  };
  
  return (
    <div className="mantine-Select-root" {...props}>
      <select className="mantine-Select-input" value={value || ''} onChange={handleChange}>
        {placeholder && <option value="">{placeholder}</option>}
        {data && data.map((item) => {
          const itemValue = typeof item === 'object' ? item.value : item;
          const itemLabel = typeof item === 'object' ? item.label : item;
          return <option key={itemValue} value={itemValue}>{itemLabel}</option>;
        })}
      </select>
    </div>
  );
};
export const Pagination = ({ total, value, onChange }) => (
  <nav className="mantine-Pagination-root" role="navigation">
    {Array.from({ length: total }, (_, i) => (
      <button
        key={i + 1}
        className={`mantine-Pagination-item ${value === i + 1 ? 'mantine-Pagination-item-active' : ''}`}
        onClick={() => onChange && onChange(i + 1)}
      >
        {i + 1}
      </button>
    ))}
  </nav>
);
export const Tooltip = ({ children, label, ...props }) => (
  <div className="mantine-Tooltip-root" title={label} {...props}>
    {children}
  </div>
);

// Grid sub-components
Grid.Col = ({ children, span, ...props }) => {
  const spanClass = typeof span === 'object' 
    ? `mantine-Grid-col-${span.base || 12}` 
    : `mantine-Grid-col-${span || 12}`;
  return (
    <div className={`mantine-Grid-col ${spanClass}`} {...props}>
      {children}
    </div>
  );
};

// Table sub-components
Table.ScrollContainer = ({ children, minWidth, ...props }) => (
  <div className="mantine-Table-scrollContainer" style={{ minWidth }} {...props}>{children}</div>
);
Table.Thead = ({ children, ...props }) => (
  <thead className="mantine-Table-thead" {...props}>{children}</thead>
);
Table.Tbody = ({ children, ...props }) => (
  <tbody className="mantine-Table-tbody" {...props}>{children}</tbody>
);
Table.Tr = ({ children, ...props }) => (
  <tr className="mantine-Table-tr" {...props}>{children}</tr>
);
Table.Th = ({ children, ...props }) => (
  <th className="mantine-Table-th" {...props}>{children}</th>
);
Table.Td = ({ children, ...props }) => (
  <td className="mantine-Table-td" {...props}>{children}</td>
);

// Tabs sub-components
Tabs.List = ({ children, ...props }) => (
  <div className="mantine-Tabs-list" {...props}>{children}</div>
);
Tabs.Tab = ({ children, leftSection, ...props }) => (
  <button className="mantine-Tabs-tab" {...props}>
    {leftSection && React.isValidElement(leftSection) && <span>{leftSection}</span>}
    {children}
  </button>
);
Tabs.Panel = ({ children, ...props }) => (
  <div className="mantine-Tabs-panel" {...props}>{children}</div>
);

// AppShell sub-components
AppShell.Header = ({ children, ...props }) => (
  <header className="mantine-AppShell-header" {...props}>{children}</header>
);
AppShell.Main = ({ children, ...props }) => (
  <main className="mantine-AppShell-main" {...props}>{children}</main>
);
AppShell.Navbar = ({ children, ...props }) => (
  <nav className="mantine-AppShell-navbar" {...props}>{children}</nav>
);
AppShell.Section = ({ children, grow, ...props }) => (
  <div className={`mantine-AppShell-section ${grow ? 'mantine-AppShell-section-grow' : ''}`} {...props}>
    {children}
  </div>
);

// MantineProvider mock
export const MantineProvider = ({ children }) => children;

// Additional exports
export const createStyles = () => ({ classes: {}, cx: (...args) => args.join(' ') });
export const useMantineTheme = () => ({ colorScheme: 'light' }); 