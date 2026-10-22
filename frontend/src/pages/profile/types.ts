import type { ProfileData } from '../../profileStore'

/** What every section of the profile page receives: the data, and a way to change it (saved at once). */
export interface SectionProps {
  data: ProfileData
  update: (change: (d: ProfileData) => ProfileData) => void
}
