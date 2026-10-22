import './Sidebar.css'

export default function Sidebar({ activePage, navigation }) {
  return (
    <aside className="sidebar">
      <div className="sidebar__brand" aria-label="Camel ETL">
        <span className="sidebar__mark" aria-hidden="true"><span /><span /></span>
        <span>Camel ETL</span>
      </div>
      <nav aria-label="Main navigation">
        {navigation.map(({ id, label, icon }) => (
          <a key={id} className="sidebar__link" href={`#${id}`}
            aria-current={activePage === id ? 'page' : undefined}>
            <svg className="sidebar__icon" viewBox="0 0 24 24" fill="none"
              stroke="currentColor" strokeWidth="1.7" strokeLinecap="round"
              strokeLinejoin="round" aria-hidden="true" focusable="false">
              <path d={icon} />
            </svg>
            <span>{label}</span>
          </a>
        ))}
      </nav>
    </aside>
  )
}
