import { useState } from 'react'
import NetworkScene from './NetworkScene.jsx'
import './Home.css'

function Arrow() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true">
      <path d="M5 12h14m-6-6 6 6-6 6" />
    </svg>
  )
}

export default function Home() {
  const [paused, setPaused] = useState(false)

  return (
    <main className="home-page" aria-labelledby="home-title">
      <header className="home-topline">
        <span>
          CAMEL ETL <span className="home-divider">/</span> WORKSPACE
        </span>
        <span className="home-edition">A space for connected data</span>
      </header>
      <section className="home-hero" aria-label="Welcome to Camel ETL">
        <NetworkScene paused={paused} />
        <div className="home-copy">
          <div className="home-eyebrow">
            <span /> EVERYTHING STARTS WITH A CONNECTION
          </div>
          <h1 id="home-title">
            Give your data
            <br />a place to <em>flow.</em>
          </h1>
          <p>
            Connect your sources. Shape your pipelines.
            <br className="home-desktop-break" /> Bring every part of your data together.
          </p>
          <a className="home-primary" href="#connectors">
            Set up a connection <Arrow />
          </a>
        </div>
        <div className="home-scene-footer">
          <span className="home-interaction-hint">
            <span aria-hidden="true">✧</span> Move your cursor. Make a connection.
          </span>
          <button
            className="home-motion"
            type="button"
            aria-pressed={paused}
            onClick={() => setPaused(!paused)}
          >
            {paused ? 'Resume motion' : 'Pause motion'}
          </button>
        </div>
      </section>
      <section className="home-start" aria-labelledby="home-start-title">
        <div className="home-section-heading">
          <h2 id="home-start-title">Your next connection starts here</h2>
          <span>EXPLORE YOUR WORKSPACE</span>
        </div>
        <div className="home-shortcuts">
          <a className="home-shortcut" href="#connectors">
            <span className="home-card-number">01 / CONNECT</span>
            <div>
              <h3>Bring your data in</h3>
              <Arrow />
            </div>
            <p>Configure and test the connections to your data sources and destinations.</p>
            <span className="home-card-link">Open connectors</span>
          </a>
          <a className="home-shortcut" href="#workflows">
            <span className="home-card-number">02 / BUILD</span>
            <div>
              <h3>Explore workflows</h3>
              <Arrow />
            </div>
            <p>A home for the paths your data will take, from source to destination.</p>
            <span className="home-card-link">Open workflows</span>
          </a>
          <a className="home-shortcut" href="#executions">
            <span className="home-card-number">03 / MONITOR</span>
            <div>
              <h3>Follow your executions</h3>
              <Arrow />
            </div>
            <p>Explore the home for run history, monitoring, logs, and retries.</p>
            <span className="home-card-link">Open executions</span>
          </a>
        </div>
      </section>
      <footer className="home-footer">
        <span>Built around connections.</span>
        <span>POWERED BY APACHE CAMEL</span>
      </footer>
    </main>
  )
}
