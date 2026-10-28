import assert from 'node:assert/strict'
import test from 'node:test'
import { validateOutputNames } from '../src/pages/mappers/mapperValidation.js'

test('requires one or more unique output names without whitespace or controls', () => {
  for (const name of [
    '',
    'Call Records',
    ' CallRecords',
    'CallRecords ',
    'Call\tRecords',
    'Call\nRecords',
    'Call\u00a0Records',
    'Call\0Records',
  ]) {
    assert.ok(validateOutputNames([{ name }]), JSON.stringify(name))
  }
  assert.ok(validateOutputNames([]))
  assert.ok(validateOutputNames([{ name: 'Calls' }, { name: 'Calls' }]))
})

test('preserves exact output names and allows distinct case-sensitive names', () => {
  const outputs = [{ name: 'CallRecords' }, { name: 'callRecords' }, { name: 'customer_records' }]
  const before = structuredClone(outputs)
  assert.equal(validateOutputNames(outputs), null)
  assert.deepEqual(outputs, before)
})
