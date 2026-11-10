import { useCallback, useState } from 'react';

const KEY = 'clinicready.sidebarCollapsed';

function read(): boolean {
  try {
    return localStorage.getItem(KEY) === '1';
  } catch {
    return false;
  }
}

/** Desktop sidebar collapsed state, persisted per browser (storage may be unavailable). */
export function useSidebarCollapsed(): [boolean, () => void] {
  const [collapsed, setCollapsed] = useState(read);
  const toggle = useCallback(() => {
    setCollapsed((prev) => {
      const next = !prev;
      try {
        localStorage.setItem(KEY, next ? '1' : '0');
      } catch {
        // ignore: preference simply won't persist
      }
      return next;
    });
  }, []);
  return [collapsed, toggle];
}
