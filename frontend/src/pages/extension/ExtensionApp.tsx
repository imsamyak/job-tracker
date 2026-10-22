import ExtApply from './ExtApply'
import ExtHome from './ExtHome'
import './extension.css'

/**
 * The Chrome extension popup: a home screen with the jobs still to apply to, and the apply screen for
 * one of them. Designed here at popup size (400 x 600); in the real popup it fills the window.
 */
export default function ExtensionApp({ hash }: { hash: string }) {
  const match = /^#\/ext\/jobs\/(\d+)$/.exec(hash)
  return (
    <div className="extstage">
      <div className="ext">
        {match ? <ExtApply key={match[1]} id={Number(match[1])} /> : <ExtHome />}
      </div>
    </div>
  )
}
