import { formatNaira, nairaToKobo } from './money'

describe('formatNaira', () => {
  it('formats whole kobo like the backend does', () => {
    expect(formatNaira(2_000_000)).toBe('₦20,000.00')
    expect(formatNaira(10_000_000)).toBe('₦100,000.00')
    expect(formatNaira(2_000_050)).toBe('₦20,000.50')
    expect(formatNaira(0)).toBe('₦0.00')
    expect(formatNaira(7)).toBe('₦0.07')
  })
})

describe('nairaToKobo', () => {
  it('converts typed amounts using string maths', () => {
    expect(nairaToKobo('20000')).toBe(2_000_000)
    expect(nairaToKobo('20,000.5')).toBe(2_000_050)
    expect(nairaToKobo('₦1,250.75')).toBe(125_075)
    expect(nairaToKobo(' 0.07 ')).toBe(7)
  })

  it('never suffers floating point errors', () => {
    // 0.1 + 0.2 and 1.15 * 100 are classic float traps
    expect(nairaToKobo('1.15')).toBe(115)
    expect(nairaToKobo('0.29')).toBe(29)
    expect(nairaToKobo('4.35')).toBe(435)
  })

  it('rejects anything that is not a clean amount', () => {
    for (const bad of ['', 'abc', '1.234', '-5', '1e5', '.5', '5.', '1,2,3x', '12.3.4']) {
      expect(nairaToKobo(bad)).toBeNull()
    }
  })

  it('rejects amounts too large to represent safely', () => {
    expect(nairaToKobo('99999999999999999999')).toBeNull()
  })
})
