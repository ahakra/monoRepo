export const newPort = (port) => ({
  rowId: crypto.randomUUID(),
  name: port?.name || '',
  kind: port?.kind || 'FIELD_LIST',
  fieldDefinitionId: port?.fieldDefinitionId || '',
})

export function changePort(port, key, value) {
  return { ...port, [key]: value, ...(key === 'kind' ? { fieldDefinitionId: '' } : {}) }
}

export function serializePorts(ports) {
  return ports.map(({ name, kind, fieldDefinitionId }) => ({
    name,
    kind,
    fieldDefinitionId: kind === 'FIELD_LIST' ? fieldDefinitionId : null,
  }))
}

export function validatePorts(ports, direction) {
  if (!ports.length || ports.length > 100) return `Add between 1 and 100 ${direction}s.`
  const names = new Set()
  for (const port of ports) {
    if (
      !port.name ||
      port.name.length > 120 ||
      /\s/u.test(port.name) ||
      [...port.name].some((character) => {
        const code = character.codePointAt(0)
        return code < 32 || (code >= 127 && code <= 159)
      })
    )
      return `Each ${direction} needs an exact name of up to 120 characters without whitespace.`
    if (names.has(port.name))
      return `Names must be unique within ${direction}s. Names are case sensitive.`
    names.add(port.name)
    if (!['FIELD_LIST', 'DYNAMIC_VARIABLE'].includes(port.kind))
      return `Choose a type for every ${direction}.`
    if (port.kind === 'FIELD_LIST' && !port.fieldDefinitionId)
      return `Choose a Fields definition for every Fields list.`
  }
  return null
}
