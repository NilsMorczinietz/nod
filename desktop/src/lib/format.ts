const time = new Intl.DateTimeFormat(undefined, { hour: '2-digit', minute: '2-digit' })
const date = new Intl.DateTimeFormat(undefined, { weekday: 'long', day: 'numeric', month: 'long' })

/** "14:02" */
export const clockTime = (epochMs: number): string => time.format(epochMs)

const startOfDay = (t: number) => new Date(t).setHours(0, 0, 0, 0)

/** "Today", "Yesterday" or "Thursday, 19 November". */
export const dayLabel = (epochMs: number, now = Date.now()): string => {
  const days = Math.round((startOfDay(now) - startOfDay(epochMs)) / 86_400_000)
  if (days === 0) return 'Today'
  if (days === 1) return 'Yesterday'
  return date.format(epochMs)
}

/** Groups items (newest first) into consecutive days. */
export const groupByDay = <T extends { createdAt: number }>(
  items: T[],
): { day: string; items: T[] }[] => {
  const groups: { day: string; items: T[] }[] = []
  for (const item of items) {
    const day = dayLabel(item.createdAt)
    const last = groups.at(-1)
    if (last?.day === day) last.items.push(item)
    else groups.push({ day, items: [item] })
  }
  return groups
}
