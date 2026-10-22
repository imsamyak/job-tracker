import LabeledList from './LabeledList'
import type { SectionProps } from './types'

/**
 * The links you paste into application forms: LinkedIn, GitHub, a portfolio, LeetCode...
 * The Links window on the apply screen shows these for one-click copying.
 */
export default function LinksSection({ data, update }: SectionProps) {
  return (
    <LabeledList
      title="Links"
      showTitle={false}
      labelHint="LinkedIn"
      valueHint="linkedin.com/in/you"
      items={data.contact.links}
      onChange={(next) => update((d) => ({ ...d, contact: { ...d.contact, links: next } }))}
    />
  )
}
