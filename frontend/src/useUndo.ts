import { useCallback, useRef, useState } from 'react'

const GROUP_MS = 800 // changes this close together (typing) count as one step
const MAX_STEPS = 100

/** State with undo and redo. Rapid changes, such as typing, are one step, so one undo takes back a whole burst. */
export function useUndo<T>(initial: () => T) {
  const [state, setState] = useState(initial)
  const current = useRef(state)
  const past = useRef<T[]>([])
  const future = useRef<T[]>([])
  const lastChange = useRef(0)

  const set = useCallback((next: T | ((prev: T) => T)) => {
    const value = typeof next === 'function' ? (next as (prev: T) => T)(current.current) : next
    if (value === current.current) return
    const now = Date.now()
    if (now - lastChange.current > GROUP_MS) {
      past.current.push(current.current)
      if (past.current.length > MAX_STEPS) past.current.shift()
    }
    lastChange.current = now
    future.current = []
    current.current = value
    setState(value)
  }, [])

  /** Returns false when there is nothing to go back to. */
  const undo = useCallback(() => {
    const previous = past.current.pop()
    if (previous === undefined) return false
    future.current.push(current.current)
    current.current = previous
    lastChange.current = 0
    setState(previous)
    return true
  }, [])

  const redo = useCallback(() => {
    const next = future.current.pop()
    if (next === undefined) return false
    past.current.push(current.current)
    current.current = next
    lastChange.current = 0
    setState(next)
    return true
  }, [])

  return { state, set, undo, redo }
}
