import type { ReactNode, SVGProps } from 'react';

/**
 * Hand-drawn 24×24 stroke icon set. Kept inline so the client ships no icon
 * dependency and every glyph inherits `currentColor`.
 */
export type IconName =
  | 'home'
  | 'camera'
  | 'truck'
  | 'pin'
  | 'archive'
  | 'globe'
  | 'bell'
  | 'user'
  | 'users'
  | 'menu'
  | 'close'
  | 'chevronRight'
  | 'chevronDown'
  | 'search'
  | 'check'
  | 'checkCircle'
  | 'arrowRight'
  | 'sparkles'
  | 'leaf'
  | 'sprout'
  | 'scale'
  | 'clock'
  | 'calendar'
  | 'phone'
  | 'shield'
  | 'upload'
  | 'image'
  | 'alert'
  | 'info'
  | 'lock'
  | 'logout'
  | 'box'
  | 'factory'
  | 'route'
  | 'trending'
  | 'filter'
  | 'refresh'
  | 'external'
  | 'plus'
  | 'settings'
  | 'recycle';

const GLYPHS: Record<IconName, ReactNode> = {
  home: <path d="M4 10.6 12 4.2l8 6.4V19a1.6 1.6 0 0 1-1.6 1.6h-3.6v-6H9.2v6H5.6A1.6 1.6 0 0 1 4 19z" />,
  camera: (
    <>
      <path d="M4.2 8.4h2.9l1.4-2h7l1.4 2h2.9A1.6 1.6 0 0 1 21.4 10v8.2a1.6 1.6 0 0 1-1.6 1.6H4.2a1.6 1.6 0 0 1-1.6-1.6V10a1.6 1.6 0 0 1 1.6-1.6Z" />
      <circle cx="12" cy="13.6" r="3.3" />
    </>
  ),
  truck: (
    <>
      <path d="M3 7.4h10.4v9.2H3z" />
      <path d="M13.4 10.6h3.9L21 14v2.6h-7.6z" />
      <circle cx="7" cy="18.4" r="1.7" />
      <circle cx="17" cy="18.4" r="1.7" />
    </>
  ),
  pin: (
    <>
      <path d="M12 21.2s6.6-5.5 6.6-10.6a6.6 6.6 0 1 0-13.2 0C5.4 15.7 12 21.2 12 21.2Z" />
      <circle cx="12" cy="10.4" r="2.5" />
    </>
  ),
  archive: (
    <>
      <path d="M4 8h16v11.4a1.6 1.6 0 0 1-1.6 1.6H5.6A1.6 1.6 0 0 1 4 19.4z" />
      <path d="M3 4.6h18V8H3z" />
      <path d="M10 12h4" />
    </>
  ),
  globe: (
    <>
      <circle cx="12" cy="12" r="8.4" />
      <path d="M3.6 12h16.8" />
      <path d="M12 3.6c2.2 2.3 3.3 5.1 3.3 8.4S14.2 18.1 12 20.4c-2.2-2.3-3.3-5.1-3.3-8.4S9.8 5.9 12 3.6Z" />
    </>
  ),
  bell: (
    <>
      <path d="M18 16.4V11a6 6 0 0 0-12 0v5.4L4.4 19h15.2z" />
      <path d="M9.8 19a2.4 2.4 0 0 0 4.4 0" />
    </>
  ),
  user: (
    <>
      <circle cx="12" cy="8.2" r="3.7" />
      <path d="M4.8 20.4a7.2 7.2 0 0 1 14.4 0" />
    </>
  ),
  users: (
    <>
      <circle cx="9.4" cy="8.4" r="3.4" />
      <path d="M3.4 20a6 6 0 0 1 12 0" />
      <path d="M16 5.6a3.4 3.4 0 0 1 0 6.6" />
      <path d="M17.4 20a6 6 0 0 0-1.6-4.1" />
    </>
  ),
  menu: (
    <>
      <path d="M4 7.5h16" />
      <path d="M4 12h16" />
      <path d="M4 16.5h16" />
    </>
  ),
  close: (
    <>
      <path d="M6.2 6.2 17.8 17.8" />
      <path d="M17.8 6.2 6.2 17.8" />
    </>
  ),
  chevronRight: <path d="M9.5 5.5 16 12l-6.5 6.5" />,
  chevronDown: <path d="M6 9.5 12 15.5l6-6" />,
  search: (
    <>
      <circle cx="10.8" cy="10.8" r="6.2" />
      <path d="M15.4 15.4 20.4 20.4" />
    </>
  ),
  check: <path d="M5 12.8l4.6 4.6L19 7.4" />,
  checkCircle: (
    <>
      <circle cx="12" cy="12" r="8.6" />
      <path d="M8.2 12.4l2.8 2.8 5-5.6" />
    </>
  ),
  arrowRight: (
    <>
      <path d="M4.5 12h14.6" />
      <path d="M13.4 6.2 19.2 12l-5.8 5.8" />
    </>
  ),
  sparkles: (
    <>
      <path d="M11.4 3.6l1.5 4.1 4.1 1.5-4.1 1.5-1.5 4.1-1.5-4.1L5.8 9.2l4.1-1.5z" />
      <path d="M18 14.6l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8z" />
    </>
  ),
  leaf: (
    <>
      <path d="M20.4 4C10.6 4 4.6 9.4 4.6 16.2V20h1.6c6.8 0 12.6-4.4 14.2-16Z" />
      <path d="M4.8 19.6C8.4 13.6 13.4 9.8 20.4 7.8" />
    </>
  ),
  sprout: (
    <>
      <path d="M12 20.6v-7" />
      <path d="M12 13.6c0-3.4-2.4-6-6-6 0 3.6 2.6 6 6 6Z" />
      <path d="M12 13.6c0-3.6 2.6-6.4 6.6-6.4 0 3.8-2.8 6.4-6.6 6.4Z" />
    </>
  ),
  scale: (
    <>
      <circle cx="12" cy="5.6" r="1.9" />
      <path d="M4.2 8.4h15.6" />
      <path d="M7.4 8.4 4 15.2a3.2 3.2 0 0 0 6 0z" />
      <path d="M16.6 8.4 13.2 15.2a3.2 3.2 0 0 0 6 0z" />
      <path d="M12 7.6v13" />
    </>
  ),
  clock: (
    <>
      <circle cx="12" cy="12" r="8.4" />
      <path d="M12 7.6v5l3.2 2" />
    </>
  ),
  calendar: (
    <>
      <rect x="3.6" y="5.4" width="16.8" height="15" rx="2" />
      <path d="M3.6 10h16.8" />
      <path d="M8.6 3.6v3.6" />
      <path d="M15.4 3.6v3.6" />
    </>
  ),
  phone: (
    <path d="M6.2 3.6h2.6l1.8 4.4-2 1.4a11.6 11.6 0 0 0 5.4 5.4l1.4-2 4.4 1.8v2.6a2 2 0 0 1-2.2 2A17.2 17.2 0 0 1 4.2 5.8a2 2 0 0 1 2-2.2Z" />
  ),
  shield: (
    <>
      <path d="M12 3.4l7 2.8v6c0 4.8-3 7.6-7 9-4-1.4-7-4.2-7-9v-6z" />
      <path d="M9.2 12.2l2.2 2.2 4.2-4.6" />
    </>
  ),
  upload: (
    <>
      <path d="M12 16.4V4.6" />
      <path d="M7.2 9.4 12 4.6l4.8 4.8" />
      <path d="M4.6 16v2.8a1.8 1.8 0 0 0 1.8 1.8h11.2a1.8 1.8 0 0 0 1.8-1.8V16" />
    </>
  ),
  image: (
    <>
      <rect x="3.6" y="4.6" width="16.8" height="14.8" rx="2.4" />
      <circle cx="9" cy="10" r="1.8" />
      <path d="M4.4 17.6 10 12.4l3.4 3.2 2.6-2.2 3.6 3.2" />
    </>
  ),
  alert: (
    <>
      <path d="M12 4.2 20.6 19.4H3.4z" />
      <path d="M12 10.2v4" />
      <path d="M12 17.2h.01" />
    </>
  ),
  info: (
    <>
      <circle cx="12" cy="12" r="8.6" />
      <path d="M12 11v5.4" />
      <path d="M12 7.8h.01" />
    </>
  ),
  lock: (
    <>
      <rect x="4.4" y="10.2" width="15.2" height="10.2" rx="2.2" />
      <path d="M8.2 10.2V7.4a3.8 3.8 0 0 1 7.6 0v2.8" />
    </>
  ),
  logout: (
    <>
      <path d="M14.6 5.4H6.4A1.8 1.8 0 0 0 4.6 7.2v9.6a1.8 1.8 0 0 0 1.8 1.8h8.2" />
      <path d="M11.4 12h9" />
      <path d="M17.8 9.2 20.6 12l-2.8 2.8" />
    </>
  ),
  box: (
    <>
      <path d="M3.6 8.2 12 4l8.4 4.2v7.6L12 20l-8.4-4.2z" />
      <path d="M3.6 8.2 12 12.4l8.4-4.2" />
      <path d="M12 12.4V20" />
    </>
  ),
  factory: (
    <>
      <path d="M4 20.4h16" />
      <path d="M6 20.4V11l4 2.4V11l4 2.4V8.6l4 2.6v9.2" />
    </>
  ),
  route: (
    <>
      <path d="M4.6 7.4l5.4-2 5.4 2 4.6-2v11.2l-4.6 2-5.4-2-5.4 2z" />
      <path d="M10 5.4v11.2" />
      <path d="M15.4 7.4v11.2" />
    </>
  ),
  trending: (
    <>
      <path d="M4 17.2l4.8-4.8 3.4 3.4L20 8.4" />
      <path d="M14.6 8.4H20v5.2" />
    </>
  ),
  filter: (
    <>
      <path d="M4 6.6h16" />
      <path d="M7 12h10" />
      <path d="M10 17.4h4" />
    </>
  ),
  refresh: (
    <>
      <path d="M20 12a8 8 0 1 1-2.6-5.9" />
      <path d="M20 4.4v4.8h-4.8" />
    </>
  ),
  external: (
    <>
      <path d="M14.2 4.8h5v5" />
      <path d="M19.2 4.8 11 13" />
      <path d="M18 14.4v4.4a1.8 1.8 0 0 1-1.8 1.8H5.8A1.8 1.8 0 0 1 4 18.8V8.2a1.8 1.8 0 0 1 1.8-1.8h4.4" />
    </>
  ),
  plus: (
    <>
      <path d="M12 5.6v12.8" />
      <path d="M5.6 12h12.8" />
    </>
  ),
  settings: (
    <>
      <path d="M4 8.4h8.4" />
      <path d="M17.4 8.4h2.6" />
      <path d="M4 15.6h3.2" />
      <path d="M12.2 15.6h7.8" />
      <circle cx="15" cy="8.4" r="2.1" />
      <circle cx="9.8" cy="15.6" r="2.1" />
    </>
  ),
  recycle: (
    <>
      <path d="M20.4 12a8.4 8.4 0 0 1-14.4 5.9" />
      <path d="M3.6 12a8.4 8.4 0 0 1 14.4-5.9" />
      <path d="M18 3.6v4.6h-4.6" />
      <path d="M6 20.4v-4.6h4.6" />
    </>
  ),
};

export interface IconProps extends Omit<SVGProps<SVGSVGElement>, 'name'> {
  name: IconName;
  size?: number;
}

export function Icon({ name, size = 20, strokeWidth = 1.6, ...rest }: IconProps) {
  return (
    <svg
      className="icon"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      {...rest}
    >
      {GLYPHS[name]}
    </svg>
  );
}
