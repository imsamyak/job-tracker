// Small hand-rolled icon set (no icon library dependency) - stroke-based, inherits currentColor/size.
import type { SVGProps } from "react";

type IconProps = SVGProps<SVGSVGElement>;

function base(props: IconProps) {
    return {
        width: 16,
        height: 16,
        viewBox: "0 0 24 24",
        fill: "none",
        stroke: "currentColor",
        strokeWidth: 2,
        strokeLinecap: "round" as const,
        strokeLinejoin: "round" as const,
        ...props,
    };
}

export function RefreshIcon(props: IconProps) {
    return (
        <svg {...base(props)}>
            <path d="M21 12a9 9 0 1 1-2.64-6.36" />
            <path d="M21 3v6h-6" />
        </svg>
    );
}

export function CheckCircleIcon(props: IconProps) {
    return (
        <svg {...base(props)}>
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
            <path d="M22 4 12 14.01l-3-3" />
        </svg>
    );
}

export function UndoCircleIcon(props: IconProps) {
    return (
        <svg {...base(props)}>
            <circle cx="12" cy="12" r="10" />
            <path d="M9.5 9.5 12 12l2.5-2.5M9.5 14.5 12 12l2.5 2.5" />
        </svg>
    );
}

export function TrashIcon(props: IconProps) {
    return (
        <svg {...base(props)}>
            <path d="M3 6h18" />
            <path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
            <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6" />
            <path d="M10 11v6M14 11v6" />
        </svg>
    );
}

export function BuildingIcon(props: IconProps) {
    return (
        <svg {...base(props)}>
            <rect x="4" y="2" width="16" height="20" rx="1" />
            <path d="M9 6h1M14 6h1M9 10h1M14 10h1M9 14h1M14 14h1" />
            <path d="M9 22v-4h6v4" />
        </svg>
    );
}

export function LinkIcon(props: IconProps) {
    return (
        <svg {...base(props)}>
            <path d="M10 13a5 5 0 0 0 7.54.54l2-2a5 5 0 0 0-7.07-7.07l-1.15 1.14" />
            <path d="M14 11a5 5 0 0 0-7.54-.54l-2 2a5 5 0 0 0 7.07 7.07l1.14-1.14" />
        </svg>
    );
}

export function CloseIcon(props: IconProps) {
    return (
        <svg {...base(props)}>
            <path d="M18 6 6 18M6 6l12 12" />
        </svg>
    );
}

export function PowerIcon(props: IconProps) {
    return (
        <svg {...base(props)}>
            <path d="M12 2v10" />
            <path d="M18.36 6.64a9 9 0 1 1-12.73 0" />
        </svg>
    );
}

export function ClockIcon(props: IconProps) {
    return (
        <svg {...base(props)}>
            <circle cx="12" cy="12" r="10" />
            <path d="M12 6v6l4 2" />
        </svg>
    );
}

export function FolderIcon(props: IconProps) {
    return (
        <svg {...base(props)}>
            <path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z" />
        </svg>
    );
}

export function MapPinIcon(props: IconProps) {
    return (
        <svg {...base(props)}>
            <path d="M12 22s7-6.1 7-12a7 7 0 1 0-14 0c0 5.9 7 12 7 12Z" />
            <circle cx="12" cy="10" r="2.5" />
        </svg>
    );
}

export function FunnelIcon(props: IconProps) {
    return (
        <svg {...base(props)}>
            <path d="M22 3H2l8 9.46V19l4 2v-8.54L22 3Z" />
        </svg>
    );
}

export function FileIcon(props: IconProps) {
    return (
        <svg {...base(props)}>
            <path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2Z" />
            <path d="M14 2v6h6" />
        </svg>
    );
}

export function DownloadIcon(props: IconProps) {
    return (
        <svg {...base(props)}>
            <path d="M12 3v12" />
            <path d="M7 10l5 5 5-5" />
            <path d="M5 21h14" />
        </svg>
    );
}

export function GripIcon(props: IconProps) {
    return (
        <svg {...base(props)}>
            <circle cx="9" cy="6" r="1" />
            <circle cx="15" cy="6" r="1" />
            <circle cx="9" cy="12" r="1" />
            <circle cx="15" cy="12" r="1" />
            <circle cx="9" cy="18" r="1" />
            <circle cx="15" cy="18" r="1" />
        </svg>
    );
}

export function MailIcon(props: IconProps) {
    return (
        <svg {...base(props)}>
            <rect x="2" y="4" width="20" height="16" rx="2" />
            <path d="m22 6-10 7L2 6" />
        </svg>
    );
}

export function SparklesIcon(props: IconProps) {
    return (
        <svg {...base(props)}>
            <path d="M12 3v4M12 17v4M3 12h4M17 12h4M5.6 5.6l2.8 2.8M15.6 15.6l2.8 2.8M18.4 5.6l-2.8 2.8M8.4 15.6l-2.8 2.8" />
        </svg>
    );
}

export function CopyIcon(props: IconProps) {
    return (
        <svg {...base(props)}>
            <rect x="9" y="9" width="13" height="13" rx="2" />
            <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
        </svg>
    );
}

export function HashIcon(props: IconProps) {
    return (
        <svg {...base(props)}>
            <path d="M4 9h16M4 15h16M10 3 8 21M16 3l-2 18" />
        </svg>
    );
}

export function PlusIcon(props: IconProps) {
    return (
        <svg {...base(props)}>
            <path d="M12 5v14M5 12h14" />
        </svg>
    );
}

export function ExternalLinkIcon(props: IconProps) {
    return (
        <svg {...base(props)}>
            <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
            <path d="M15 3h6v6M10 14 21 3" />
        </svg>
    );
}

export function SortIcon(props: IconProps) {
    return (
        <svg {...base(props)}>
            <path d="M11 5h10M11 9h7M11 13h4" />
            <path d="m3 17 3 3 3-3M6 18V4" />
        </svg>
    );
}

export function HelpCircleIcon(props: IconProps) {
    return (
        <svg {...base(props)}>
            <circle cx="12" cy="12" r="10" />
            <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 2-3 4" />
            <line x1="12" y1="17" x2="12.01" y2="17" />
        </svg>
    );
}

export function SendIcon(props: IconProps) {
    return (
        <svg {...base(props)}>
            <path d="m22 2-7 20-4-9-9-4Z" />
            <path d="M22 2 11 13" />
        </svg>
    );
}
