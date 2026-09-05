import { Page } from './page.module';

export type OnboardingFormat = 'XLSX' | 'CSV';
export type OnboardingSection = 'INSTRUMENTS' | 'USERS' | 'INVENTORY' | 'CATEGORIES' | 'ACCOUNTS' | 'OPENING_BALANCES';
export type OnboardingJobStatus = 'UPLOADED' | 'VALIDATING' | 'INVALID' | 'READY' | 'APPLYING' | 'COMPENSATING' | 'COMPLETED' | 'FAILED' | 'COMPENSATION_REQUIRED' | 'CANCELLED';
export type OnboardingRowStatus = 'VALID' | 'WARNING' | 'ERROR' | 'APPLIED' | 'SKIPPED';
export interface OnboardingCounts { total: number; valid: number; warnings: number; errors: number; }
export interface OnboardingJob { id: number; fileName: string; format: OnboardingFormat; csvSection?: OnboardingSection; templateVersion: number; status: OnboardingJobStatus; stage: string; progressPercentage: number; counts: OnboardingCounts; createdAt: string; completedAt?: string; setupEmailFailures: number; lastErrorCode?: string; }
export interface OnboardingLimits { maxFileSizeBytes: number; maxTotalRows: number; maxUserRows: number; }
export interface OnboardingContext { tenantCode: string; tenantName: string; schemaActive: boolean; maxUsers?: number; users: number; instruments: number; inventoryItems: number; financialAccounts: number; supportedTemplateVersions: number[]; lastImport?: OnboardingJob; limits: OnboardingLimits; }
export interface OnboardingSectionSummary { section: OnboardingSection; total: number; valid: number; warnings: number; errors: number; create: number; reuse: number; skip: number; applied: number; }
export interface OnboardingRow { id: number; section: OnboardingSection; rowNumber: number; status: OnboardingRowStatus; action: 'CREATE' | 'REUSE' | 'SKIP'; values: Record<string, string | number | boolean | null>; }
export interface OnboardingIssue { id: number; severity: 'ERROR' | 'WARNING'; code: string; section?: OnboardingSection; rowNumber?: number; columnName?: string; message: string; suggestion?: string; }
export type OnboardingPage<T> = Page<T>;
