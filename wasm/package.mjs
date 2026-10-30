import { chmod, copyFile, mkdir, readFile } from 'node:fs/promises'

const output = new URL('../examples/telecom/wasm/', import.meta.url)
await mkdir(output, { recursive: true })
for (const name of ['parse_usage', 'project_cdr', 'project_sms', 'summarize_usage']) {
  const source = new URL(`target/wasm32-unknown-unknown/release/${name}.wasm`, import.meta.url)
  const bytes = await readFile(source)
  if (!WebAssembly.validate(bytes)) throw new Error(`Invalid WASM: ${name}`)
  const file = `${name.replaceAll('_', '-')}.wasm`
  await copyFile(source, new URL(file, output))
  await chmod(new URL(file, output), 0o644)
  console.log(`Built examples/telecom/wasm/${file}`)
}
