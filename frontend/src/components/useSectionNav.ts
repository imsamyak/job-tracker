import { useEffect, useRef, useState } from 'react'

/**
 * For a page that is one long scroll of sections with a navigation bar beside it: which section you are in
 * while scrolling (the one whose top has passed a line just under the top bar; the last one at the very
 * bottom), and a function that scrolls to a section when its entry is clicked. Each section's element id is
 * {@code prefix + sectionId}.
 */
export function useSectionNav(ids: string[], prefix: string) {
  const [active, setActive] = useState(ids[0])
  const holdUntil = useRef(0) // after a click, ignore scroll updates while the smooth scroll runs
  const key = ids.join('|')

  useEffect(() => {
    const list = key.split('|')
    const onScroll = () => {
      if (Date.now() < holdUntil.current) return
      const line = 140
      let current = list[0]
      for (const id of list) {
        const el = document.getElementById(prefix + id)
        if (el && el.getBoundingClientRect().top <= line) current = id
      }
      const atBottom = window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 4
      setActive(atBottom ? list[list.length - 1] : current)
    }
    onScroll()
    window.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('resize', onScroll)
    return () => {
      window.removeEventListener('scroll', onScroll)
      window.removeEventListener('resize', onScroll)
    }
  }, [key, prefix])

  const goTo = (id: string) => {
    holdUntil.current = Date.now() + 900
    setActive(id)
    document.getElementById(prefix + id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  return { active, goTo }
}
