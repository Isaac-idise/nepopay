const grouped = new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

/** The server stores money as whole kobo. 2000000 -> "₦20,000.00" (same shape the backend uses in its messages). */
export function formatNaira(kobo: number): string {
  return `₦${grouped.format(kobo / 100)}`
}

/**
 * Turns what a person typed ("20000", "20,000.5", "₦1,250.75") into kobo using string maths only,
 * so there is never a floating-point rounding error. Returns null if it is not a valid amount.
 */
export function nairaToKobo(input: string): number | null {
  const cleaned = input.replace(/[₦,\s]/g, '')
  if (!/^\d+(\.\d{1,2})?$/.test(cleaned)) return null
  const [whole, fraction = ''] = cleaned.split('.')
  const kobo = Number(whole) * 100 + Number(fraction.padEnd(2, '0'))
  return Number.isSafeInteger(kobo) ? kobo : null
}
