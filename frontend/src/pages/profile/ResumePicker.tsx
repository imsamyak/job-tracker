import { useRef, type ReactNode } from 'react'

/** A button that asks for a PDF and hands it over. */
export default function ResumePicker({ label, icon, onPick, primary, disabled }: {
  label: string
  /** Shows this instead of the label (the label stays as the tooltip). */
  icon?: ReactNode
  onPick: (f: File) => void
  primary?: boolean
  disabled?: boolean
}) {
  const input = useRef<HTMLInputElement>(null)
  return (
    <>
      <input
        ref={input}
        type="file"
        accept="application/pdf,.pdf"
        hidden
        onChange={(e) => {
          const f = e.target.files?.[0]
          e.target.value = ''
          if (f) onPick(f)
        }}
      />
      <button className={(primary ? 'btn btn--primary' : 'btn') + (icon ? ' btn--icon' : '')} title={label} aria-label={label} disabled={disabled} onClick={() => input.current?.click()}>
        {icon ?? label}
      </button>
    </>
  )
}
