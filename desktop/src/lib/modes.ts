import type { RoutingMode } from '@nod/protocol'

/** The routing modes as offered in the UI, with one sentence each. */
export const MODES: { value: RoutingMode; label: string; explanation: string }[] = [
  {
    value: 'away',
    label: "When I'm away",
    explanation: 'Cards show here. Your phone stays quiet while you are at this PC.',
  },
  {
    value: 'always',
    label: 'Every time',
    explanation: 'Every request shows here and on your phone at the same time.',
  },
  {
    value: 'updates',
    label: 'Updates only',
    explanation: 'Only finished and failed tasks are sent. Claude Code asks in the terminal.',
  },
  {
    value: 'terminal',
    label: 'Terminal',
    explanation: 'Nothing is sent. Claude Code asks in the terminal as usual.',
  },
]
