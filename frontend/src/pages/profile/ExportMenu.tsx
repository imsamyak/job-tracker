import { useEffect, useRef, useState } from 'react'
import { CheckIcon, CopyIcon, DownloadIcon } from '../../components/Icons'
import type { ProfileData } from '../../profileStore'

/** Export: your whole profile as JSON, to download as a file or copy to the clipboard. */
export default function ExportMenu({ data }: { data: ProfileData }) {
  const [open, setOpen] = useState(false)
  const [copied, setCopied] = useState(false)
  const box = useRef<HTMLDivElement>(null)
  const json = () => JSON.stringify(data, null, 2)

  useEffect(() => {
    if (!open) return
    const onDown = (e: MouseEvent) => {
      if (box.current && !box.current.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  const download = () => {
    const url = URL.createObjectURL(new Blob([json()], { type: 'application/json' }))
    const a = document.createElement('a')
    a.href = url
    a.download = 'jobtracker-profile.json'
    a.click()
    URL.revokeObjectURL(url)
    setOpen(false)
  }

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(json())
    } catch {
      // clipboard blocked: select the text in a hidden box and copy that way
      const t = document.createElement('textarea')
      t.value = json()
      document.body.appendChild(t)
      t.select()
      document.execCommand('copy')
      t.remove()
    }
    setCopied(true)
    setTimeout(() => { setCopied(false); setOpen(false) }, 1200)
  }

  return (
    <div className="export" ref={box}>
      <button className="btn btn--icon" title="Export profile" aria-label="Export profile" aria-haspopup="menu" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
        <DownloadIcon />
      </button>
      {open && (
        <div className="export__menu" role="menu">
          <button role="menuitem" onClick={download}><DownloadIcon /> Download JSON</button>
          <button role="menuitem" onClick={() => void copy()}>{copied ? <CheckIcon /> : <CopyIcon />} {copied ? 'Copied' : 'Copy JSON'}</button>
        </div>
      )}
    </div>
  )
}
