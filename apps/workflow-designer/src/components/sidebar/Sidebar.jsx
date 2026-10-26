import './Sidebar.css'

export default function Sidebar({ activePage, navigation }) {
  return (
    <aside className="sidebar">
      <a className="sidebar__brand" href="#home" aria-label="Camel ETL — Home">
        {/* ETL Branch logo */}
        <svg
          className="sidebar__mark"
          viewBox="0 0 60 60"
          fill="none"
          stroke="currentColor"
          strokeWidth="3"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <rect x="5" y="20" width="12" height="20" rx="3" />
          <path d="M17 30h12m0 0v-14h12m-12 14v14h12" />
          <rect x="41" y="10" width="13" height="12" rx="2" />
          <rect x="41" y="38" width="13" height="12" rx="2" />
        </svg>

        <span>Camel ETL</span>
      </a>

      <nav aria-label="Main navigation">
        {navigation.map(({ id, label, icon }) => (
          <a
            key={id}
            className="sidebar__link"
            href={`#${id}`}
            aria-current={activePage === id ? 'page' : undefined}
          >
            <svg
              className="sidebar__icon"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.7"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
              focusable="false"
            >
              <path d={icon} />
            </svg>

            <span>{label}</span>
          </a>
        ))}
      </nav>
    </aside>
  )
}