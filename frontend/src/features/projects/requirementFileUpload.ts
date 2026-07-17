import type { AppDatabase } from '@/db/appDatabase'

export interface UploadPrivacySetting {
  isAccepted(): Promise<boolean>
  accept(): Promise<void>
}

export type RequirementChunkProcessor = (
  chunk: string,
  index: number,
  total: number,
  signal: AbortSignal,
) => void | Promise<void>

export function createUploadPrivacySetting(
  database: Pick<AppDatabase, 'app_setting'>,
): UploadPrivacySetting {
  return {
    async isAccepted() {
      return Boolean((await database.app_setting.get('uploadPrivacyNoticeAccepted'))?.value)
    },
    async accept() {
      await database.app_setting.put({
        key: 'uploadPrivacyNoticeAccepted',
        value: true,
        updatedAt: new Date().toISOString(),
      })
    },
  }
}
