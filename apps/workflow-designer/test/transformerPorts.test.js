import assert from 'node:assert/strict'
import test from 'node:test'
import {
  newPort,
  changePort,
  serializePorts,
  validatePorts,
} from '../src/pages/dynamic/transformerPorts.js'

test('switching to a dynamic variable clears the prior Fields reference and strips UI metadata', () => {
  const port = newPort({ name: 'Calls', kind: 'FIELD_LIST', fieldDefinitionId: 'fields-id' })
  const variable = changePort(port, 'kind', 'DYNAMIC_VARIABLE')
  assert.equal(variable.name, 'Calls')
  assert.equal(variable.fieldDefinitionId, '')
  assert.deepEqual(serializePorts([variable]), [
    { name: 'Calls', kind: 'DYNAMIC_VARIABLE', fieldDefinitionId: null },
  ])
  assert.equal(port.fieldDefinitionId, 'fields-id')
  assert.equal(changePort(variable, 'kind', 'FIELD_LIST').fieldDefinitionId, '')
})

test('supports mixed ports and validates each direction independently', () => {
  const ports = [
    newPort({ name: 'Calls', fieldDefinitionId: 'fields-id' }),
    newPort({ name: 'calls', kind: 'DYNAMIC_VARIABLE' }),
  ]
  assert.equal(validatePorts(ports, 'input'), null)
  assert.equal(validatePorts(ports, 'output'), null)
  assert.deepEqual(serializePorts(ports), [
    { name: 'Calls', kind: 'FIELD_LIST', fieldDefinitionId: 'fields-id' },
    { name: 'calls', kind: 'DYNAMIC_VARIABLE', fieldDefinitionId: null },
  ])
  assert.ok(validatePorts([ports[0], ports[0]], 'input'))
  assert.ok(validatePorts([], 'output'))
  assert.ok(validatePorts([newPort({ name: 'Bad name', kind: 'DYNAMIC_VARIABLE' })], 'input'))
  assert.ok(validatePorts([newPort({ name: 'Calls' })], 'output'))
})
