const readLocks = [
  'none',
  'markerFile',
  'changed',
  'fileLock',
  'rename',
  'idempotent',
  'idempotent-changed',
  'idempotent-rename',
]

export default function FileFields({ form, onChange }) {
  function text(name, label, placeholder, help, required = false) {
    return (
      <label key={name}>
        {label}
        <input
          name={name}
          value={form[name]}
          onChange={onChange}
          required={required}
          maxLength={2048}
          placeholder={placeholder}
        />
        {help && <small>{help}</small>}
      </label>
    )
  }
  function number(name, label, min, help, max) {
    return (
      <label key={name}>
        {label}
        <input
          type="number"
          name={name}
          value={form[name]}
          onChange={onChange}
          required
          min={min}
          max={max}
          step="1"
        />
        {help && <small>{help}</small>}
      </label>
    )
  }
  return (
    <>
      {text(
        'directoryName',
        'Source directory (directoryName)',
        '/data/inbox',
        'Directory on the Java service host. Enter a literal path, not a file: URI or a dynamic expression.',
        true,
      )}
      {text(
        'move',
        'Processed directory (move)',
        '.processed',
        'After successful processing. A relative folder is resolved from the consumed file’s parent; Camel File expressions are also supported.',
        true,
      )}
      {text(
        'moveFailed',
        'Invalid / failed directory (moveFailed)',
        '.invalid',
        'For files whose processing fails. Business validation must mark the exchange as failed to send a file here.',
        true,
      )}
      {['recursive', 'autoCreate'].map((name) => (
        <label className="config-create-database" key={name}>
          <span>
            <input name={name} type="checkbox" checked={form[name]} onChange={onChange} />
            {name === 'recursive'
              ? 'Include subdirectories (recursive)'
              : 'Create missing source directory when running (autoCreate)'}
          </span>
          <small>
            {name === 'recursive'
              ? 'Poll nested directories as well as the source directory.'
              : 'Saving and Test directories do not create folders.'}
          </small>
        </label>
      ))}
      <details className="config-connection-advanced">
        <summary>File filters, polling, and read locks</summary>
        <div className="config-fields">
          {text(
            'fileName',
            'Filename filter / expression (fileName)',
            'e.g. orders.csv',
            'Optional exact filename or Camel File expression.',
          )}
          {text(
            'preMove',
            'In-progress directory (preMove)',
            '.inprogress',
            'Optional move before processing begins; may use a Camel File expression.',
          )}
          {text(
            'include',
            'Include filename regex (include)',
            '.*\\.csv',
            'Optional case-insensitive regular expression; enter the pattern directly without URI RAW(...) wrapping.',
          )}
          {text(
            'exclude',
            'Exclude filename regex (exclude)',
            '.*\\.tmp',
            'Optional case-insensitive regular expression.',
          )}
          {text(
            'includeExt',
            'Include extensions (includeExt)',
            'csv,txt',
            'Comma-separated extensions, without leading dots.',
          )}
          {text(
            'excludeExt',
            'Exclude extensions (excludeExt)',
            'tmp,part',
            'Comma-separated extensions to skip.',
          )}
          {text(
            'charset',
            'Character encoding (charset)',
            'UTF-8',
            'Optional; leave blank to use Camel’s default encoding.',
          )}
          <label>
            Read-lock strategy (readLock)
            <select name="readLock" value={form.readLock} onChange={onChange}>
              {readLocks.map((value) => (
                <option key={value} value={value}>
                  {value}
                </option>
              ))}
            </select>
            <small>
              Use changed for files that may still be uploading. none matches Camel’s default.
            </small>
          </label>
          {number('delay', 'Polling interval, ms (delay)', 1)}
          {number('initialDelay', 'Initial delay, ms (initialDelay)', 0)}
          {number(
            'maxMessagesPerPoll',
            'Files per poll (maxMessagesPerPoll)',
            0,
            '0 means unlimited.',
            2147483647,
          )}
          {number(
            'readLockTimeout',
            'Read-lock timeout, ms (readLockTimeout)',
            0,
            'Must exceed the check interval for timed strategies. 0 waits indefinitely.',
          )}
          {number(
            'readLockCheckInterval',
            'Read-lock check interval, ms (readLockCheckInterval)',
            1,
          )}
          {number(
            'readLockMinAge',
            'Minimum file age, ms (readLockMinAge)',
            0,
            'Applies to the changed strategy.',
          )}
          {number(
            'readLockMinLength',
            'Minimum file size, bytes (readLockMinLength)',
            0,
            'Applies to changed. Use 0 to allow empty files.',
          )}
        </div>
      </details>
    </>
  )
}
