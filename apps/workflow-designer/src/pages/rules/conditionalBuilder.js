export const operators = {
  EQUALS: 'Equals',
  NOT_EQUALS: 'Does not equal',
  GREATER_THAN: 'Greater than',
  GREATER_OR_EQUAL: 'Greater than or equal',
  LESS_THAN: 'Less than',
  LESS_OR_EQUAL: 'Less than or equal',
  STARTS_WITH: 'Starts with',
  ENDS_WITH: 'Ends with',
  CONTAINS: 'Contains',
  IS_NULL: 'Is null / missing',
  IS_NOT_NULL: 'Is not null / missing',
}
const numeric = (type) => ['integer', 'long', 'decimal'].includes(type)
const text = (type) => ['string', 'text'].includes(type)
export const compatible = (left, right) =>
  left === right || (numeric(left) && numeric(right)) || (text(left) && text(right))
export const unary = (operator) => ['IS_NULL', 'IS_NOT_NULL'].includes(operator)
export const operatorsFor = (type) =>
  Object.keys(operators).filter((operator) => {
    if (unary(operator) || ['EQUALS', 'NOT_EQUALS'].includes(operator)) return true
    if (['STARTS_WITH', 'ENDS_WITH', 'CONTAINS'].includes(operator)) return text(type)
    return numeric(type) || text(type) || ['date', 'datetime'].includes(type)
  })
export const comparison = () => ({
  type: 'COMPARISON',
  field: '',
  operator: 'EQUALS',
  operandType: 'VALUE',
  value: '',
})
export const group = () => ({ type: 'GROUP', junction: 'AND', children: [comparison()] })
export const countNodes = (node) =>
  1 + (node.children || []).reduce((sum, child) => sum + countNodes(child), 0)

// JSON literal inputs are text while editing, and actual JSON values in API requests.
export function mapLiterals(node, fields, editing = false) {
  if (node.type === 'GROUP')
    return {
      type: 'GROUP',
      junction: node.junction,
      children: node.children.map((child) => mapLiterals(child, fields, editing)),
    }
  const base = { type: 'COMPARISON', field: node.field, operator: node.operator }
  if (unary(node.operator)) return base
  if (node.operandType === 'FIELD')
    return { ...base, operandType: 'FIELD', otherField: node.otherField || '' }
  let value = node.value
  if (fields[node.field] === 'json') {
    if (editing) value = JSON.stringify(value)
    else {
      try {
        value = JSON.parse(value)
      } catch {
        throw new Error(`Enter valid JSON for ${node.field}.`)
      }
    }
  }
  return { ...base, operandType: 'VALUE', value: value === undefined ? '' : value }
}

export function summarize(node) {
  if (node.type === 'GROUP') return `(${node.children.map(summarize).join(` ${node.junction} `)})`
  return `${node.field} ${operators[node.operator]?.toLowerCase()}${unary(node.operator) ? '' : ` ${node.operandType === 'FIELD' ? node.otherField : JSON.stringify(node.value)}`}`
}
