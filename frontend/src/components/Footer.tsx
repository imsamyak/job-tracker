import './Footer.css'

/** Where "Connect" goes. Change this one line to point at LinkedIn, a portfolio or an email. */
const CONNECT_URL = 'mailto:imsamyak18@gmail.com?subject=Hello%20from%20JobTracker'

/** A quiet line at the bottom of every page: who made the app, that they are open to work, and how to reach them. */
export default function Footer() {
  return (
    <footer className="footer">
      <div className="footer__inner">
        <span>Made by <strong>Samyak Jain</strong></span>
        <span className="footer__sep" aria-hidden>&middot;</span>
        <span className="footer__open">
          <span className="footer__dot" aria-hidden />
          Open to Work
        </span>
        <span className="footer__sep" aria-hidden>&middot;</span>
        <a className="footer__connect" href={CONNECT_URL}>
          Connect <span aria-hidden>&rarr;</span>
        </a>
      </div>
    </footer>
  )
}
