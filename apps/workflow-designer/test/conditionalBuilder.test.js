import assert from 'node:assert/strict'
import test from 'node:test'
import {
  compatible,
  operatorsFor,
  mapLiterals,
  countNodes,
  summarize,
} from '../src/pages/rules/conditionalBuilder.js'

test('limits comparison operators and comparison fields to compatible types', () => {
  assert.equal(compatible('datetime', 'datetime'), true)
  assert.equal(compatible('integer', 'decimal'), true)
  assert.equal(compatible('string', 'text'), true)
  assert.equal(compatible('datetime', 'date'), false)
  assert.equal(compatible('boolean', 'string'), false)
  assert.equal(operatorsFor('datetime').includes('GREATER_THAN'), true)
  assert.equal(operatorsFor('datetime').includes('CONTAINS'), false)
  assert.equal(operatorsFor('boolean').includes('GREATER_THAN'), false)
  assert.equal(operatorsFor('json').includes('EQUALS'), true)
})

test('round trips nested groups, field operands, JSON literals, and precise numeric strings', () => {
  const expression = {
    type: 'GROUP',
    junction: 'AND',
    children: [
      {
        type: 'COMPARISON',
        field: 'end',
        operator: 'GREATER_THAN',
        operandType: 'FIELD',
        otherField: 'start',
      },
      {
        type: 'GROUP',
        junction: 'OR',
        children: [
          {
            type: 'COMPARISON',
            field: 'data',
            operator: 'EQUALS',
            operandType: 'VALUE',
            value: { a: [1, null, true] },
          },
          {
            type: 'COMPARISON',
            field: 'count',
            operator: 'EQUALS',
            operandType: 'VALUE',
            value: '9007199254740993',
          },
        ],
      },
    ],
  }
  const fields = { start: 'datetime', end: 'datetime', data: 'json', count: 'long' }
  const editing = mapLiterals(expression, fields, true)
  assert.equal(editing.children[1].children[0].value, '{"a":[1,null,true]}')
  assert.deepEqual(mapLiterals(editing, fields), expression)
  assert.equal(countNodes(expression), 5)
  assert.equal(
    summarize(expression),
    '(end greater than start AND (data equals {"a":[1,null,true]} OR count equals "9007199254740993"))',
  )
})

test('omits inactive operands so null checks and field comparisons have valid API shapes', () => {
  assert.deepEqual(
    mapLiterals(
      { type: 'COMPARISON', field: 'a', operator: 'IS_NULL', operandType: 'VALUE', value: 'old' },
      {},
    ),
    { type: 'COMPARISON', field: 'a', operator: 'IS_NULL' },
  )
  assert.deepEqual(
    mapLiterals(
      {
        type: 'COMPARISON',
        field: 'a',
        operator: 'EQUALS',
        operandType: 'FIELD',
        otherField: 'b',
        value: 'old',
      },
      {},
    ),
    { type: 'COMPARISON', field: 'a', operator: 'EQUALS', operandType: 'FIELD', otherField: 'b' },
  )
})

test('rejects malformed JSON and preserves empty text and false values', () => {
  const literal = (value) => ({
    type: 'COMPARISON',
    field: 'data',
    operator: 'EQUALS',
    operandType: 'VALUE',
    value,
  })
  assert.throws(() => mapLiterals(literal('{broken'), { data: 'json' }), /valid JSON/)
  assert.equal(mapLiterals(literal(''), { data: 'string' }).value, '')
  assert.equal(mapLiterals(literal(false), { data: 'boolean' }).value, false)
  assert.equal(mapLiterals(literal('null'), { data: 'json' }).value, null)
})
