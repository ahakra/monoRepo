export const RULE_BLOCKS = {
  conditionalRule: {
    title: 'Conditional rule',
    icon: 'conditional',
    outputs: [
      { id: 'matched', label: 'Matched' },
      { id: 'not-matched', label: 'Not matched' },
    ],
  },
  normalizeRule: {
    title: 'Normalize rule',
    icon: 'normalize',
    outputs: [{ id: 'out', label: 'Output' }],
  },
}
