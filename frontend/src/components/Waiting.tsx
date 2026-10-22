import { api, type Intake } from '../api'
import './Waiting.css'

/** Job descriptions that are being read, could not be read, or turned out to be in the list already. */
export default function Waiting({ items, onChange }: { items: Intake[]; onChange: () => void }) {
  if (items.length === 0) return null

  const act = async (fn: () => Promise<unknown>) => {
    try {
      await fn()
    } finally {
      onChange()
    }
  }

  return (
    <ul className="waiting">
      {items.map((i) => {
        const reading = i.status === 'PENDING' || i.status === 'PROCESSING'
        return (
          <li key={i.id} className={`waiting__item waiting__item--${i.status.toLowerCase()}`}>
            <span className="waiting__icon" aria-hidden>
              {reading ? <span className="spinner" /> : i.status === 'FAILED' ? '!' : 'i'}
            </span>
            <div className="waiting__text">
              <div className="waiting__label">{i.label}</div>
              <div className="waiting__status">
                {i.status === 'PENDING' && 'Waiting to be read...'}
                {i.status === 'PROCESSING' && 'Reading...'}
                {(i.status === 'FAILED' || i.status === 'DUPLICATE') && i.message}
              </div>
            </div>
            {i.status === 'FAILED' && <button className="link" onClick={() => act(() => api.retry(i.id))}>Try again</button>}
            {!reading && <button className="link" onClick={() => act(() => api.dismiss(i.id))}>Dismiss</button>}
          </li>
        )
      })}
    </ul>
  )
}
