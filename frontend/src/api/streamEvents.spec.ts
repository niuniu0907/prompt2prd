import { describe, expect, it } from 'vitest'

import {
  STREAM_EVENT_TYPES,
  StreamProtocolError,
  parseStreamEvent,
} from './streamEvents'

const requestId = '53be8d9c-8949-4476-8835-8f12080257ac'

describe('stream event protocol', () => {
  it('defines the same fifteen event names as the backend', () => {
    expect(STREAM_EVENT_TYPES).toEqual([
      'analysis_started',
      'analysis_progress',
      'requirement_patch',
      'question_created',
      'conflict_detected',
      'completeness_changed',
      'architecture_candidate',
      'architecture_confirmed',
      'section_delta',
      'section_started',
      'section_completed',
      'section_failed',
      'generation_aborted',
      'generation_completed',
      'generation_failed',
    ])
  })

  it('parses a valid known event', () => {
    const event = parseStreamEvent({
      requestId,
      eventId: 1,
      type: 'analysis_progress',
      data: { progress: 25, message: '正在提取需求' },
      timestamp: '2026-07-17T10:00:00Z',
    })

    expect(event.known).toBe(true)
    expect(event.type).toBe('analysis_progress')
  })

  it('returns an unknown event for forward compatibility', () => {
    const event = parseStreamEvent({
      requestId,
      eventId: 2,
      type: 'future_event',
      data: { anything: true },
      timestamp: '2026-07-17T10:00:00Z',
    })

    expect(event).toMatchObject({ known: false, type: 'future_event' })
  })

  it('rejects malformed envelopes and malformed known payloads', () => {
    expect(() => parseStreamEvent({
      requestId,
      eventId: 0,
      type: 'analysis_started',
      data: { phase: 'analysis' },
      timestamp: 'not-a-date',
    })).toThrow(StreamProtocolError)

    expect(() => parseStreamEvent({
      requestId,
      eventId: 3,
      type: 'analysis_progress',
      data: { progress: 20 },
      timestamp: '2026-07-17T10:00:00Z',
    })).toThrow(/message/)
  })
})
