import { useCallback, useRef, useState } from 'react'

/** Copies text to the clipboard and remembers which item was copied for two seconds (to show a tick). */
export function useCopy() {
  const [copied, setCopied] = useState<string | null>(null)
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined)

  const copy = useCallback(async (key: string, text: string) => {
    try {
      await navigator.clipboard.writeText(text)
    } catch {
      return // clipboard blocked: the text is still on screen to select by hand
    }
    setCopied(key)
    clearTimeout(timer.current)
    timer.current = setTimeout(() => setCopied(null), 2000)
  }, [])

  return { copied, copy }
}

/** localStorage that never throws (private windows, blocked storage): the screen just forgets on reload. */
export function readStored<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key)
    return raw ? (JSON.parse(raw) as T) : fallback
  } catch {
    return fallback
  }
}

export function writeStored(key: string, value: unknown) {
  try {
    localStorage.setItem(key, JSON.stringify(value))
  } catch {
    /* not worth surfacing */
  }
}
