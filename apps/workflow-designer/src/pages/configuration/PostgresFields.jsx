export default function PostgresFields({ form, onChange }) {
  return (
    <>
      <label>
        Host
        <input
          name="host"
          value={form.host}
          onChange={onChange}
          required
          maxLength={253}
          pattern="[a-zA-Z0-9.\-]+"
        />
        <small>Hostname or IP address reachable by the ETL service.</small>
      </label>
      <label>
        Port
        <input
          name="port"
          type="number"
          value={form.port}
          onChange={onChange}
          required
          min="1"
          max="65535"
        />
      </label>
      <label>
        Database
        <input
          name="database"
          value={form.database}
          onChange={onChange}
          required
          maxLength={63}
          pattern="[a-zA-Z0-9_\-]+"
          placeholder="e.g. analytics"
        />
      </label>
      <label>
        SSL mode
        <select name="sslMode" value={form.sslMode} onChange={onChange}>
          <option value="disable">Disabled · local development</option>
          <option value="require">Required</option>
          <option value="verify-full">Verify certificate & hostname</option>
        </select>
      </label>
      <label>
        Username
        <input
          name="username"
          value={form.username}
          onChange={onChange}
          required
          maxLength={120}
          autoComplete="off"
        />
      </label>
      <label>
        Password
        <input
          name="password"
          type="password"
          value={form.password}
          onChange={onChange}
          maxLength={4096}
          autoComplete="new-password"
          placeholder={
            form.hasPassword ? 'Leave blank to keep saved password' : 'Database password'
          }
        />
        <small>Stored by the service; never returned to the browser.</small>
      </label>
      <details className="config-connection-advanced">
        <summary>Advanced PostgreSQL settings</summary>
        <div className="config-fields">
          <label>
            Default schema / search path (currentSchema)
            <input
              name="currentSchema"
              value={form.currentSchema}
              onChange={onChange}
              maxLength={2048}
              placeholder="e.g. public,analytics"
            />
            <small>
              Optional comma-separated schemas for unqualified table names. Leave blank to use the
              database user’s search path.
            </small>
          </label>
          <label>
            Connection timeout, seconds (connectTimeout)
            <input
              name="connectTimeout"
              type="number"
              value={form.connectTimeout}
              onChange={onChange}
              required
              min="0"
              max="2147484"
              step="1"
            />
            <small>Maximum time to establish a connection. 0 disables this timeout.</small>
          </label>
          <label>
            Socket read timeout, seconds (socketTimeout)
            <input
              name="socketTimeout"
              type="number"
              value={form.socketTimeout}
              onChange={onChange}
              required
              min="0"
              max="2147484"
              step="1"
            />
            <small>Maximum time waiting for data from PostgreSQL. 0 disables this timeout.</small>
          </label>
          <label>
            SSL root certificate path (sslrootcert)
            <input
              name="sslrootcert"
              value={form.sslrootcert}
              onChange={onChange}
              maxLength={2048}
              placeholder="e.g. /opt/etl/certs/root.crt"
            />
            <small>
              Optional CA certificate on the Java service host for SSL verification. Leave blank to
              use the driver’s default certificate location.
            </small>
          </label>
        </div>
      </details>
      <label className="config-create-database">
        <span>
          <input
            name="createDatabaseIfNotExists"
            type="checkbox"
            checked={!!form.createDatabaseIfNotExists}
            onChange={onChange}
          />{' '}
          Create database if it doesn’t exist
        </span>
        <small>
          “Create & test” creates a missing database using this account. The account needs
          permission to create databases. Saving alone does not create it.
        </small>
      </label>
    </>
  )
}
