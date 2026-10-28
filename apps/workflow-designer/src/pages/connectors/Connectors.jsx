import Connections from '../configuration/Connections.jsx'
import '../configuration/Configuration.css'
import './Connectors.css'

export default function Connectors() {
  return (
    <main
      id="connectors"
      className="page config connectors-page"
      aria-labelledby="connectors-title"
    >
      <div className="config-eyebrow">WORKSPACE / CONNECTORS</div>
      <header className="config-header">
        <div>
          <h1 id="connectors-title">Connectors</h1>
          <p>Connect sources and destinations through files, FTP/SFTP, SQL, and APIs.</p>
          <p>
            File and PostgreSQL connectors are available now. FTP/SFTP and API connectors are
            planned.
          </p>
        </div>
      </header>
      <Connections />
    </main>
  )
}
