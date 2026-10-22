/**
 * Locations are shown as "City, ST" with the state's two-letter code: "Pune, MH", "Delhi, DL", "Patna, BR".
 * The full text stays stored as it was read; this only decides how it is shown. A state is taken from
 * the text when it is there (a code or a full name); otherwise it is looked up from the city.
 */

const STATES: Record<string, string> = {
  'andhra pradesh': 'AP', 'arunachal pradesh': 'AR', assam: 'AS', bihar: 'BR', chhattisgarh: 'CG', goa: 'GA',
  gujarat: 'GJ', haryana: 'HR', 'himachal pradesh': 'HP', jharkhand: 'JH', karnataka: 'KA', kerala: 'KL',
  'madhya pradesh': 'MP', maharashtra: 'MH', manipur: 'MN', meghalaya: 'ML', mizoram: 'MZ', nagaland: 'NL',
  odisha: 'OD', orissa: 'OD', punjab: 'PB', rajasthan: 'RJ', sikkim: 'SK', 'tamil nadu': 'TN', telangana: 'TG',
  tripura: 'TR', 'uttar pradesh': 'UP', uttarakhand: 'UK', 'west bengal': 'WB',
  'andaman and nicobar islands': 'AN', chandigarh: 'CH', delhi: 'DL', 'jammu and kashmir': 'JK', ladakh: 'LA',
  lakshadweep: 'LD', puducherry: 'PY', pondicherry: 'PY',
  'dadra and nagar haveli and daman and diu': 'DN',
}

const CITY_STATE: Record<string, string> = {
  pune: 'MH', mumbai: 'MH', 'navi mumbai': 'MH', thane: 'MH', nagpur: 'MH', nashik: 'MH', aurangabad: 'MH',
  bengaluru: 'KA', bangalore: 'KA', mysuru: 'KA', mysore: 'KA', mangaluru: 'KA', hubballi: 'KA', belagavi: 'KA',
  chennai: 'TN', coimbatore: 'TN', madurai: 'TN', tiruchirappalli: 'TN', salem: 'TN',
  hyderabad: 'TG', secunderabad: 'TG', warangal: 'TG',
  delhi: 'DL', 'new delhi': 'DL',
  gurugram: 'HR', gurgaon: 'HR', faridabad: 'HR', panchkula: 'HR',
  noida: 'UP', 'greater noida': 'UP', ghaziabad: 'UP', lucknow: 'UP', kanpur: 'UP', agra: 'UP', varanasi: 'UP',
  kolkata: 'WB', howrah: 'WB',
  ahmedabad: 'GJ', gandhinagar: 'GJ', surat: 'GJ', vadodara: 'GJ', rajkot: 'GJ',
  jaipur: 'RJ', jodhpur: 'RJ', udaipur: 'RJ',
  kochi: 'KL', cochin: 'KL', thiruvananthapuram: 'KL', trivandrum: 'KL', kozhikode: 'KL',
  indore: 'MP', bhopal: 'MP', jabalpur: 'MP',
  chandigarh: 'CH', mohali: 'PB', ludhiana: 'PB', amritsar: 'PB',
  patna: 'BR', gaya: 'BR',
  bhubaneswar: 'OD', cuttack: 'OD',
  visakhapatnam: 'AP', vizag: 'AP', vijayawada: 'AP', guntur: 'AP',
  guwahati: 'AS', ranchi: 'JH', jamshedpur: 'JH', dehradun: 'UK', raipur: 'CG', panaji: 'GA', puducherry: 'PY',
}

export function formatLocation(location: string | null): string | null {
  if (!location) return null
  const parts = location.split(',').map((p) => p.trim()).filter(Boolean)
  if (parts.length === 0) return null
  const city = parts[0]
  if (/^remote$/i.test(city)) return 'Remote'

  for (const part of parts.slice(1)) {
    const p = part.toLowerCase()
    if (p === 'india' || p === 'in') continue // the country is not a state
    if (/^[a-z]{2}$/.test(p)) return `${city}, ${p.toUpperCase()}`
    if (STATES[p]) return `${city}, ${STATES[p]}`
  }
  const known = CITY_STATE[city.toLowerCase()]
  return known ? `${city}, ${known}` : city
}
