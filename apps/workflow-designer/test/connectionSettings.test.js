import assert from 'node:assert/strict'
import test from 'node:test'
import {
  connectionPayload,
  decodeConnection,
  newConnection,
} from '../src/pages/configuration/connectionSettings.js'

test('File payloads use Camel keys and do not include stale database credentials or metadata', () => {
  const form = {
    ...newConnection('file'),
    name: 'Inbox',
    directoryName: '/data/input files',
    include: 'a+b.*\\.csv',
    move: 'processed/${file:onlyname}',
    recursive: true,
    host: 'db',
    username: 'user',
    password: 'secret',
    id: 'file-id',
  }
  const payload = connectionPayload(form)
  assert.equal(payload.type, 'file')
  assert.equal(payload.password, '')
  assert.equal(payload.settings.directoryName, form.directoryName)
  assert.equal(payload.settings.include, form.include)
  assert.equal(payload.settings.move, form.move)
  assert.equal(payload.settings.recursive, 'true')
  assert.equal(payload.settings.autoCreate, 'true')
  assert.equal('host' in payload.settings, false)
  assert.equal('id' in payload.settings, false)
})

test('decodes File booleans and defaults without changing timestamps or paths', () => {
  const form = decodeConnection({
    id: 'file-id',
    name: 'Inbox',
    type: 'file',
    createdAt: '2026-10-28T12:00:00Z',
    settings: {
      directoryName: '/data/inbox',
      recursive: 'true',
      autoCreate: 'false',
      move: '.done',
    },
  })
  assert.equal(form.recursive, true)
  assert.equal(form.autoCreate, false)
  assert.equal(form.moveFailed, '.invalid')
  assert.equal(form.createdAt, '2026-10-28T12:00:00Z')
})

test('keeps PostgreSQL serialization compatible and type defaults independent', () => {
  const file = newConnection('file')
  const pg = newConnection('postgres')
  pg.name = 'Database'
  pg.password = 'secret'
  pg.createDatabaseIfNotExists = true
  const payload = connectionPayload(pg)
  assert.equal(payload.password, 'secret')
  assert.equal(payload.settings.createDatabaseIfNotExists, 'true')
  assert.equal('directoryName' in payload.settings, false)
  assert.equal('host' in file, false)
  assert.equal(
    decodeConnection({ type: 'postgres', settings: { createDatabaseIfNotExists: 'false' } })
      .createDatabaseIfNotExists,
    false,
  )
})

test('round trips configurable PostgreSQL properties and supplies defaults for old connectors', () => {
  const form = {
    ...newConnection('postgres'),
    name: 'Analytics',
    currentSchema: '"Customer Data",public',
    connectTimeout: '30',
    socketTimeout: '0',
    sslrootcert: '/opt/SSL Certificates/root.crt',
  }
  const payload = connectionPayload(form)
  assert.equal(payload.settings.currentSchema, form.currentSchema)
  assert.equal(payload.settings.connectTimeout, '30')
  assert.equal(payload.settings.socketTimeout, '0')
  assert.equal(payload.settings.sslrootcert, form.sslrootcert)
  const decoded = decodeConnection({ type: 'postgres', settings: payload.settings })
  assert.equal(decoded.sslrootcert, form.sslrootcert)
  assert.equal(decoded.currentSchema, form.currentSchema)
  const old = decodeConnection({ type: 'postgres', settings: { host: 'db', port: '5432' } })
  assert.equal(old.connectTimeout, '5')
  assert.equal(old.socketTimeout, '10')
  assert.equal(old.currentSchema, '')
  assert.equal(old.sslrootcert, '')
})
