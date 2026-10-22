import { useCallback, useEffect, useState } from 'react'
import { api, type ResumeInfo } from '../../api'

/** The one sample resume the app keeps. */
export function useResume() {
  const [info, setInfo] = useState<ResumeInfo | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api.resume().then(setInfo).catch(() => setInfo({ present: false, filename: null, size: null, uploadedAt: null }))
  }, [])

  const upload = useCallback(async (file: File) => {
    setError('')
    try {
      setInfo(await api.uploadResume(file))
      return true
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not add the resume.')
      return false
    }
  }, [])

  return { info, error, upload }
}
