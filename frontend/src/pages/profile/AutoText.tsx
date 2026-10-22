import { useLayoutEffect, useRef } from 'react'

/** A text box that grows with its text, so the whole of it is always visible and it never scrolls inside. */
export default function AutoText({ value, onChange, label }: {
  value: string
  onChange: (v: string) => void
  label: string
}) {
  const box = useRef<HTMLTextAreaElement>(null)
  useLayoutEffect(() => {
    const t = box.current
    if (!t) return
    t.style.height = 'auto'
    t.style.height = `${t.scrollHeight}px`
  }, [value])

  return <textarea ref={box} className="tpl__text" rows={4} value={value} aria-label={label} onChange={(e) => onChange(e.target.value)} />
}
