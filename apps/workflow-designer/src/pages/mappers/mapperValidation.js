export function validateOutputNames(outputs) {
  if (!outputs.length || outputs.length > 100) return 'Add between 1 and 100 output lists.'
  const names = new Set()
  for (const output of outputs) {
    if (
      !output.name ||
      output.name.length > 120 ||
      /\s/u.test(output.name) ||
      [...output.name].some((character) => {
        const code = character.codePointAt(0)
        return code < 32 || (code >= 127 && code <= 159)
      })
    ) {
      return 'Every output list needs an exact name of up to 120 characters with no spaces or whitespace.'
    }
    if (names.has(output.name))
      return 'Output list names must be unique within a mapper. Names are case sensitive.'
    names.add(output.name)
  }
  return null
}
