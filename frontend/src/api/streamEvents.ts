export const STREAM_EVENT_TYPES = [
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
] as const

export type StreamEventType = (typeof STREAM_EVENT_TYPES)[number]

const requiredDataFields: Record<StreamEventType, readonly string[]> = {
  analysis_started: ['phase'],
  analysis_progress: ['progress', 'message'],
  requirement_patch: ['path', 'operation', 'value'],
  question_created: ['question'],
  conflict_detected: ['conflict'],
  completeness_changed: ['previous', 'current', 'missingInformation'],
  architecture_candidate: ['candidate'],
  architecture_confirmed: ['architectureId'],
  section_delta: ['sectionId', 'delta'],
  section_started: ['sectionId', 'title'],
  section_completed: ['sectionId', 'status'],
  section_failed: ['sectionId', 'errorCode', 'retryable'],
  generation_aborted: ['reason', 'completedStages'],
  generation_completed: ['nextStage', 'finalState'],
  generation_failed: ['errorCode', 'retryable'],
}

interface EventEnvelope {
  requestId: string
  eventId: number
  type: string
  data: Record<string, unknown>
  timestamp: string
}

export interface KnownStreamEvent extends EventEnvelope {
  known: true
  type: StreamEventType
}

export interface UnknownStreamEvent extends EventEnvelope {
  known: false
}

export type ParsedStreamEvent = KnownStreamEvent | UnknownStreamEvent

export class StreamProtocolError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'StreamProtocolError'
  }
}

export function parseStreamEvent(value: unknown): ParsedStreamEvent {
  if (!isRecord(value)) {
    throw new StreamProtocolError('Stream event must be an object')
  }
  const requestId = requireUuid(value.requestId)
  const eventId = requirePositiveInteger(value.eventId, 'eventId')
  const type = requireString(value.type, 'type')
  const data = requireRecord(value.data, 'data')
  const timestamp = requireTimestamp(value.timestamp)
  if (!isKnownType(type)) {
    return { known: false, requestId, eventId, type, data, timestamp }
  }
  for (const field of requiredDataFields[type]) {
    if (!(field in data) || data[field] === null || data[field] === undefined) {
      throw new StreamProtocolError(`${type} requires data.${field}`)
    }
  }
  return { known: true, requestId, eventId, type, data, timestamp }
}

export function isTerminalEvent(event: KnownStreamEvent): boolean {
  return event.type === 'generation_completed'
    || event.type === 'generation_failed'
    || event.type === 'generation_aborted'
}

function isKnownType(value: string): value is StreamEventType {
  return (STREAM_EVENT_TYPES as readonly string[]).includes(value)
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function requireRecord(value: unknown, name: string): Record<string, unknown> {
  if (!isRecord(value)) {
    throw new StreamProtocolError(`${name} must be an object`)
  }
  return value
}

function requireString(value: unknown, name: string): string {
  if (typeof value !== 'string' || value.trim() === '') {
    throw new StreamProtocolError(`${name} must be a non-empty string`)
  }
  return value
}

function requireUuid(value: unknown): string {
  const text = requireString(value, 'requestId')
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(text)) {
    throw new StreamProtocolError('requestId must be a UUID')
  }
  return text
}

function requirePositiveInteger(value: unknown, name: string): number {
  if (typeof value !== 'number' || !Number.isInteger(value) || value < 1) {
    throw new StreamProtocolError(`${name} must be a positive integer`)
  }
  return value
}

function requireTimestamp(value: unknown): string {
  const text = requireString(value, 'timestamp')
  if (!/^\d{4}-\d{2}-\d{2}T/.test(text) || Number.isNaN(Date.parse(text))) {
    throw new StreamProtocolError('timestamp must be an ISO-8601 value')
  }
  return text
}
