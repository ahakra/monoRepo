import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { loadModule } from '../host.mjs'

const artifact = (name) => new URL(`../../examples/telecom/wasm/${name}.wasm`, import.meta.url)
const sample = await readFile(new URL('../../examples/telecom/input/usage.csv', import.meta.url))

test('compiled mapper accepts raw bytes and returns the named, typed usage list', async () => {
  const mapper = await loadModule(artifact('parse-usage'))
  assert.equal(mapper.manifest.kind, 'mapper')
  const { usage } = mapper.process(sample)
  assert.equal(usage.length, 7)
  assert.equal(usage[0].calling, ' 00961 71 123456 ')
  assert.equal(usage[0].duration_seconds, 125)
  assert.equal(usage[0].charge_amount, 0.25)
  assert.equal(usage[0].sms_count, null)
  assert.equal(usage[0].normalizedcalling, null)
  assert.match(usage[0].id, /^[0-9a-f]{8}-[0-9a-f]{4}-5[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
  assert.deepEqual(
    Object.keys(usage[0]).sort(),
    Object.keys(mapper.manifest.outputs[0].fields).sort(),
  )
  assert.deepEqual(mapper.process(sample), { usage })
})

test('compiled projections retain all and only fields declared on each named output', async () => {
  const mapper = await loadModule(artifact('parse-usage'))
  const { usage } = mapper.process(sample)
  for (const kind of ['cdr', 'sms']) {
    const transformer = await loadModule(artifact(`project-${kind}`))
    const input = usage.filter((record) => record.record_type === kind.toUpperCase())
    const outputs = transformer.transform({ usage: input })
    assert.deepEqual(Object.keys(outputs), [kind])
    assert.equal(outputs[kind].length, input.length)
    outputs[kind].forEach((record, index) => {
      assert.deepEqual(
        Object.keys(record).sort(),
        Object.keys(transformer.manifest.outputs[0].fields).sort(),
      )
      for (const key of Object.keys(record)) assert.deepEqual(record[key], input[index][key])
    })
    assert.deepEqual(transformer.transform({ usage: [] }), { [kind]: [] })
    assert.throws(() => transformer.transform({ Usage: input }), /input ports/)
    assert.throws(() => transformer.transform({ usage: input, extra: [] }), /input ports/)
    assert.throws(() => transformer.transform({ usage: {} }), /record array/)
    assert.throws(() => transformer.transform({ usage: [null] }), /record must be an object/)
    assert.throws(() => transformer.transform({ usage: [{}] }), /Missing field/)
    assert.throws(
      () => transformer.process(Buffer.from('{"abiVersion":2,"inputs":{"usage":[]}}')),
      /abiVersion/,
    )
  }
})

test('transformer supports multiple named list and dynamic ports', async () => {
  const mapper = await loadModule(artifact('parse-usage'))
  const { usage } = mapper.process(sample)
  const transformer = await loadModule(artifact('summarize-usage'))
  const output = transformer.transform({ usage, batch: 'batch-001' })
  assert.deepEqual(Object.keys(output).sort(), ['cdr', 'rejected', 'sms', 'summary'])
  // This example partitions by type only; validation/normalization remain graph rules.
  assert.deepEqual(output.summary, {
    batch: 'batch-001',
    cdrCount: 3,
    smsCount: 3,
    rejectedCount: 1,
  })
  assert.equal(output.cdr.length, 3)
  assert.equal(output.sms.length, 3)
  assert.equal(output.rejected.length, 1)
  assert.throws(() => transformer.transform({ usage }), /input ports/)
  assert.throws(() => transformer.transform({ usage, batch: 123 }), /batch must/)
})

test('CSV errors cross the ABI as errors, never as partial successes', async () => {
  const mapper = await loadModule(artifact('parse-usage'))
  for (const bytes of [
    new Uint8Array(),
    Buffer.from([0xff]),
    Buffer.from('wrong,header\n1,2'),
    Buffer.from(sample.toString().replace(',125,', ',not-an-integer,')),
    Buffer.from(sample.toString().replace(',0.25,', ',NaN,')),
    Buffer.from(sample.toString().replace('CALL-001', '')),
  ]) {
    assert.throws(() => mapper.process(bytes), /INVALID_INPUT/)
  }
  assert.equal(mapper.process(Buffer.from(sample.toString().split('\n')[0] + '\n')).usage.length, 0)
  assert.equal(
    mapper.process(Buffer.concat([Buffer.from([0xef, 0xbb, 0xbf]), sample])).usage.length,
    7,
  )
})

test('repeated large calls survive memory growth and buffer release', async () => {
  const mapper = await loadModule(artifact('parse-usage'))
  const [header, ...rows] = sample.toString().trimEnd().split('\n')
  const large = Buffer.from(`${header}\n${Array(500).fill(rows[0]).join('\n')}\n`)
  for (let iteration = 0; iteration < 15; iteration++) {
    assert.equal(mapper.process(large).usage.length, 500)
    assert.equal(mapper.process(sample).usage.length, 7)
  }
})
