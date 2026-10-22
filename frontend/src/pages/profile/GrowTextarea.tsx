import { useLayoutEffect, useRef, type TextareaHTMLAttributes } from 'react'

/** A text box that grows to show all of its text, so there is never a scroll bar inside it. */
export default function GrowTextarea(props: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const box = useRef<HTMLTextAreaElement>(null)
  useLayoutEffect(() => {
    const t = box.current
    if (!t) return
    t.style.height = 'auto'
    t.style.height = `${t.scrollHeight + 2}px`
  }, [props.value])
  return <textarea {...props} ref={box} className={`grow ${props.className ?? ''}`.trim()} />
}
