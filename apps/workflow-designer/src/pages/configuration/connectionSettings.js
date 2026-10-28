export const fileDefaults = {
  directoryName: '',
  move: '.processed',
  moveFailed: '.invalid',
  preMove: '',
  fileName: '',
  include: '',
  exclude: '',
  includeExt: '',
  excludeExt: '',
  charset: '',
  recursive: false,
  autoCreate: true,
  delay: '500',
  initialDelay: '1000',
  maxMessagesPerPoll: '0',
  readLock: 'none',
  readLockTimeout: '10000',
  readLockCheckInterval: '1000',
  readLockMinAge: '0',
  readLockMinLength: '1',
}
const postgresDefaults = {
  host: 'localhost',
  port: '5432',
  database: '',
  username: '',
  sslMode: 'disable',
  currentSchema: '',
  connectTimeout: '5',
  socketTimeout: '10',
  sslrootcert: '',
  createDatabaseIfNotExists: false,
}
const booleanKeys = { file: ['recursive', 'autoCreate'], postgres: ['createDatabaseIfNotExists'] }
const defaults = { file: fileDefaults, postgres: postgresDefaults }

export function newConnection(type = 'postgres') {
  return { name: '', type, password: '', ...defaults[type] }
}

export function connectionPayload(form) {
  const keys = Object.keys(defaults[form.type] || {})
  if (!keys.length) throw new Error('Choose a supported connector type.')
  const settings = Object.fromEntries(
    keys.map((key) => [
      key,
      booleanKeys[form.type].includes(key)
        ? String(!!form[key])
        : String(form[key] ?? defaults[form.type][key]),
    ]),
  )
  return {
    name: form.name,
    type: form.type,
    password: form.type === 'file' ? '' : form.password,
    settings,
  }
}

export function decodeConnection({ settings, ...connection }) {
  const form = { ...newConnection(connection.type), ...connection, ...settings }
  for (const key of booleanKeys[connection.type] || []) {
    const value = settings[key] ?? defaults[connection.type][key]
    form[key] = value === true || value === 'true'
  }
  return form
}
