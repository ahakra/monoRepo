// Small reference host for ABI verification. Java will implement this contract in the engine.
import { readFile } from 'node:fs/promises'

export async function loadModule(path) {
  const module = await WebAssembly.compile(await readFile(path))
  if (WebAssembly.Module.imports(module).length) throw new Error('Unexpected module imports')
  const { exports: wasm } = await WebAssembly.instantiate(module, {})
  for (const name of ['etl_alloc', 'etl_free', 'etl_process', 'etl_manifest', 'etl_abi_version']) {
    if (typeof wasm[name] !== 'function') throw new Error(`Missing export ${name}`)
  }
  if (!(wasm.memory instanceof WebAssembly.Memory) || wasm.etl_abi_version() !== 1) {
    throw new Error('Unsupported WASM ABI')
  }

  function decode(packed) {
    const unsigned = BigInt.asUintN(64, packed)
    const pointer = Number(unsigned >> 32n)
    const length = Number(unsigned & 0xffffffffn)
    if (!pointer || !length || pointer + length > wasm.memory.buffer.byteLength) {
      throw new Error('Invalid result buffer')
    }
    try {
      // Reacquire memory after every WASM call: memory.grow invalidates old views.
      return JSON.parse(
        new TextDecoder('utf-8', { fatal: true }).decode(
          new Uint8Array(wasm.memory.buffer, pointer, length),
        ),
      )
    } finally {
      wasm.etl_free(pointer, length)
    }
  }

  const manifest = decode(wasm.etl_manifest())
  return {
    manifest,
    process(bytes) {
      if (!(bytes instanceof Uint8Array)) throw new Error('Expected a byte array')
      if (bytes.length > 64 * 1024 * 1024) throw new Error('Input exceeds 64 MiB')
      const pointer = bytes.length ? wasm.etl_alloc(bytes.length) >>> 0 : 0
      if (bytes.length && !pointer) throw new Error('Input allocation failed')
      try {
        if (bytes.length) new Uint8Array(wasm.memory.buffer, pointer, bytes.length).set(bytes)
        const result = decode(wasm.etl_process(pointer, bytes.length))
        if (result.abiVersion !== 1) throw new Error('Unsupported response version')
        if (result.error) throw new Error(`${result.error.code}: ${result.error.message}`)
        const expected = manifest.outputs.map((port) => port.name).sort()
        if (JSON.stringify(Object.keys(result.outputs).sort()) !== JSON.stringify(expected)) {
          throw new Error('Output names do not match manifest')
        }
        return result.outputs
      } finally {
        if (pointer) wasm.etl_free(pointer, bytes.length)
      }
    },
    transform(inputs) {
      return this.process(new TextEncoder().encode(JSON.stringify({ abiVersion: 1, inputs })))
    },
  }
}
