const base = {
  width: 18,
  height: 18,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 2,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
  'aria-hidden': true,
}

/** Paper plane: send an application. */
export function ApplyIcon() {
  return (
    <svg {...base}>
      <path d="M22 2 11 13" />
      <path d="M22 2 15 22l-4-9-9-4 20-7Z" />
    </svg>
  )
}

export function TrashIcon() {
  return (
    <svg {...base}>
      <path d="M3 6h18" />
      <path d="M8 6V4h8v2" />
      <path d="M19 6l-1 14H6L5 6" />
      <path d="M10 11v6M14 11v6" />
    </svg>
  )
}

export function ChevronIcon() {
  return (
    <svg {...base} width={16} height={16} strokeWidth={2.5}>
      <path d="m6 9 6 6 6-6" />
    </svg>
  )
}

/** Circular arrows: change a status. */
export function RefreshIcon() {
  return (
    <svg {...base} width={16} height={16}>
      <path d="M21 12a9 9 0 0 1-15.5 6.2L3 16" />
      <path d="M3 12a9 9 0 0 1 15.5-6.2L21 8" />
      <path d="M21 3v5h-5" />
      <path d="M3 21v-5h5" />
    </svg>
  )
}

/** Funnel: filter the list. */
export function FilterIcon() {
  return (
    <svg {...base} width={16} height={16}>
      <path d="M3 5h18l-7 8v6l-4-2v-4L3 5Z" />
    </svg>
  )
}

/** Sort direction: up or down arrow when this column is the active sort, a quiet up/down pair when it is not. */
export function SortIcon({ dir }: { dir: 'asc' | 'desc' | null }) {
  const s = { ...base, width: 13, height: 13, strokeWidth: 2.4 }
  if (dir === 'asc') return <svg {...s}><path d="M12 19V5" /><path d="m6 11 6-6 6 6" /></svg>
  if (dir === 'desc') return <svg {...s}><path d="M12 5v14" /><path d="m6 13 6 6 6-6" /></svg>
  return <svg {...s}><path d="m8 9 4-4 4 4" /><path d="m8 15 4 4 4-4" /></svg>
}

export function PencilIcon() {
  return (
    <svg {...base} width={16} height={16}>
      <path d="M12 20h9" />
      <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z" />
    </svg>
  )
}

export function MailIcon() {
  return (
    <svg {...base} width={15} height={15}>
      <rect x="3" y="5" width="18" height="14" rx="2" />
      <path d="m3 7 9 6 9-6" />
    </svg>
  )
}

export function PhoneIcon() {
  return (
    <svg {...base} width={15} height={15}>
      <path d="M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.1 4.2 2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.1 1 .4 1.9.7 2.8a2 2 0 0 1-.5 2.1L8.1 9.9a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.4c.9.3 1.8.6 2.8.7a2 2 0 0 1 1.7 2Z" />
    </svg>
  )
}

export function UserIcon() {
  return (
    <svg {...base} width={16} height={16}>
      <circle cx="12" cy="8" r="4" />
      <path d="M4 21a8 8 0 0 1 16 0" />
    </svg>
  )
}

/** Person with a plus: add a contact. */
export function UserPlusIcon() {
  return (
    <svg {...base} width={18} height={18}>
      <circle cx="9" cy="8" r="4" />
      <path d="M2 21a7 7 0 0 1 14 0" />
      <path d="M19 8v6M16 11h6" />
    </svg>
  )
}

export function LinkIcon() {
  return (
    <svg {...base} width={15} height={15}>
      <path d="M10 13a5 5 0 0 0 7.1 0l3-3a5 5 0 0 0-7.1-7.1l-1.7 1.7" />
      <path d="M14 11a5 5 0 0 0-7.1 0l-3 3a5 5 0 0 0 7.1 7.1l1.7-1.7" />
    </svg>
  )
}

export function CopyIcon() {
  return (
    <svg {...base} width={15} height={15}>
      <rect x="9" y="9" width="12" height="12" rx="2" />
      <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
    </svg>
  )
}

export function CheckIcon() {
  return (
    <svg {...base} width={15} height={15} strokeWidth={2.6}>
      <path d="m5 12 5 5L20 7" />
    </svg>
  )
}

export function FileIcon() {
  return (
    <svg {...base} width={18} height={18}>
      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6Z" />
      <path d="M14 2v6h6M8 13h8M8 17h5" />
    </svg>
  )
}

export function SparklesIcon() {
  return (
    <svg {...base} width={15} height={15}>
      <path d="M12 3l1.8 4.7L18.5 9.5l-4.7 1.8L12 16l-1.8-4.7L5.5 9.5l4.7-1.8L12 3Z" />
      <path d="M19 15l.8 2.2L22 18l-2.2.8L19 21l-.8-2.2L16 18l2.2-.8L19 15Z" />
    </svg>
  )
}

export function ExternalIcon() {
  return (
    <svg {...base} width={15} height={15}>
      <path d="M14 3h7v7M10 14 21 3" />
      <path d="M21 14v5a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5" />
    </svg>
  )
}

export function InfoIcon() {
  return (
    <svg {...base} width={18} height={18}>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 11v5M12 8h.01" />
    </svg>
  )
}

/** Person in a circle: the profile button. */
export function UserCircleIcon() {
  return (
    <svg {...base} width={22} height={22} strokeWidth={1.8}>
      <circle cx="12" cy="12" r="10" />
      <circle cx="12" cy="10" r="3.2" />
      <path d="M5.8 18.4a7 7 0 0 1 12.4 0" />
    </svg>
  )
}

export function BriefcaseIcon() {
  return (
    <svg {...base} width={19} height={19}>
      <rect x="3" y="7" width="18" height="13" rx="2" />
      <path d="M8 7V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M3 13h18" />
    </svg>
  )
}

export function FolderIcon() {
  return (
    <svg {...base} width={19} height={19}>
      <path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7Z" />
    </svg>
  )
}

export function UsersIcon() {
  return (
    <svg {...base} width={19} height={19}>
      <circle cx="9" cy="8" r="3.5" />
      <path d="M2.5 20a6.5 6.5 0 0 1 13 0" />
      <circle cx="17" cy="9" r="2.5" />
      <path d="M17.5 14.2A5.5 5.5 0 0 1 21.5 19" />
    </svg>
  )
}

export function PlugIcon() {
  return (
    <svg {...base} width={19} height={19}>
      <path d="M9 3v5M15 3v5" />
      <path d="M6 8h12v3a6 6 0 0 1-12 0V8Z" />
      <path d="M12 17v4" />
    </svg>
  )
}

export function UploadIcon() {
  return (
    <svg {...base} width={18} height={18}>
      <path d="M12 16V4M7 9l5-5 5 5" />
      <path d="M4 16v3a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-3" />
    </svg>
  )
}

/** A page with an up arrow inside it: add a document. */
export function ResumeUploadIcon() {
  return (
    <svg {...base} width={20} height={20}>
      <path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8l-5-5Z" />
      <path d="M14 3v5h5" />
      <path d="M12 17v-6M9.5 13.2 12 10.7l2.5 2.5" />
    </svg>
  )
}

export function GlobeIcon() {
  return (
    <svg {...base} width={19} height={19}>
      <circle cx="12" cy="12" r="9" />
      <path d="M3 12h18" />
      <path d="M12 3c2.6 2.4 4 5.6 4 9s-1.4 6.6-4 9c-2.6-2.4-4-5.6-4-9s1.4-6.6 4-9Z" />
    </svg>
  )
}

export function BranchIcon() {
  return (
    <svg {...base} width={20} height={20}>
      <circle cx="6" cy="5" r="2" />
      <circle cx="6" cy="19" r="2" />
      <circle cx="18" cy="9" r="2" />
      <path d="M6 7v10M18 11c0 4-6 3-12 6" />
    </svg>
  )
}

export function SearchIcon() {
  return (
    <svg {...base} width={17} height={17}>
      <circle cx="11" cy="11" r="6.5" />
      <path d="m16 16 4.5 4.5" />
    </svg>
  )
}

export function PlusIcon() {
  return (
    <svg {...base} width={19} height={19}>
      <path d="M12 5v14M5 12h14" />
    </svg>
  )
}

export function ArrowLeftIcon() {
  return (
    <svg {...base} width={19} height={19}>
      <path d="M19 12H5M11 6l-6 6 6 6" />
    </svg>
  )
}

export function CloseIcon() {
  return (
    <svg {...base} width={18} height={18}>
      <path d="M6 6l12 12M18 6 6 18" />
    </svg>
  )
}

export function EyeIcon() {
  return (
    <svg {...base} width={18} height={18}>
      <path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12Z" />
      <circle cx="12" cy="12" r="2.8" />
    </svg>
  )
}

export function SendIcon() {
  return (
    <svg {...base} width={18} height={18}>
      <path d="M12 19V6M6 11l6-6 6 6" />
    </svg>
  )
}

export function GearIcon() {
  return (
    <svg {...base} width={18} height={18}>
      <circle cx="12" cy="12" r="3" />
      <path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3h0a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8v0a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1Z" />
    </svg>
  )
}

export function PuzzleIcon() {
  return (
    <svg {...base} width={19} height={19}>
      <path d="M10 4a2 2 0 1 1 4 0v1h3a1 1 0 0 1 1 1v3h-1a2 2 0 1 0 0 4h1v3a1 1 0 0 1-1 1h-3v-1a2 2 0 1 0-4 0v1H7a1 1 0 0 1-1-1v-3h1a2 2 0 1 0 0-4H6V6a1 1 0 0 1 1-1h3V4Z" />
    </svg>
  )
}

export function BellIcon() {
  return (
    <svg {...base} width={20} height={20}>
      <path d="M6 9a6 6 0 1 1 12 0c0 5 2 6 2 6H4s2-1 2-6Z" />
      <path d="M10 19a2 2 0 0 0 4 0" />
    </svg>
  )
}

export function DownloadIcon() {
  return (
    <svg {...base} width={18} height={18}>
      <path d="M12 4v11M7 11l5 5 5-5" />
      <path d="M4 16v3a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-3" />
    </svg>
  )
}

export function CodeIcon() {
  return (
    <svg {...base} width={19} height={19}>
      <path d="m8 7-5 5 5 5M16 7l5 5-5 5M14 4l-4 16" />
    </svg>
  )
}
