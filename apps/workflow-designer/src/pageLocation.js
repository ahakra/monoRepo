export function readLocation(hash) {
  const raw = hash.replace(/^#/, '')
  const split = raw.indexOf('?')
  return {
    page: split === -1 ? raw : raw.slice(0, split),
    search: split === -1 ? '' : raw.slice(split + 1),
  }
}
