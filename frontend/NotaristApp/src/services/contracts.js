// Service interface contracts (Sprint 3, Task 6).
//
// These JSDoc typedefs are the ONLY thing screens and state slices are allowed to depend on. Each
// service is a stable seam: today it delegates to the api/* modules, which internally choose a mock
// fixture or a real HTTP call based on a FEATURES flag. When Claude-1 ships the real endpoints, only
// the api/* implementation and the flag change — no screen, no state slice, and none of these
// contracts move. `usingMock` lets a screen render <MockBanner> honestly without knowing transport.
//
// This file intentionally exports no runtime code; it documents the shapes. Keeping it as a module
// (not just comments) means the contracts are import-resolvable and show up in the dependency graph.

/**
 * @typedef {Object} Paged
 * @property {Array<any>} items
 * @property {{ number:number, size:number, totalElements:number, totalPages:number }} page
 */

/**
 * @typedef {Object} CaseServiceContract
 * @property {boolean} usingMock
 * @property {(q?:{page?:number,size?:number,query?:string,status?:string}) => Promise<Paged>} listCases
 * @property {(id:string) => Promise<Object>} getCase
 */

/**
 * @typedef {Object} BundleServiceContract
 * @property {boolean} usingMock
 * @property {(caseId:string) => Promise<Array>} listBundles
 * @property {(bundleId:string) => Promise<Object>} getBundle
 * @property {(bundleId:string) => Promise<Array>} getDocuments
 */

/**
 * @typedef {Object} ReminderServiceContract
 * @property {boolean} usingMock
 * @property {() => Promise<Array>} listReminders
 */

/**
 * @typedef {Object} DashboardServiceContract
 * @property {boolean} usingMock
 * @property {() => Promise<Object>} getSummary
 */

/**
 * @typedef {Object} TimelineServiceContract
 * @property {boolean} usingMock
 * @property {(caseId:string) => Promise<Array>} getCaseTimeline
 */

/**
 * @typedef {Object} VerificationServiceContract
 * @property {boolean} usingMock
 * @property {(bundleId:string) => Promise<Array>} getChecklist
 * @property {(bundleId:string, decisions:Array) => Promise<Object>} submit
 */

/**
 * @typedef {Object} OCRServiceContract
 * @property {boolean} usingMock
 * @property {(documentId:string) => Promise<Object>} getFields
 * @property {(documentId:string, fieldId:string, decision:string, value?:string) => Promise<Object>} submitFieldDecision
 */

/**
 * @typedef {Object} SearchServiceContract
 * @property {boolean} usingMock
 * @property {(q:{query:string,mode:string}) => Promise<Object>} run
 * @property {() => Promise<Array>} getRecent
 * @property {() => Promise<Array>} getSaved
 */

/**
 * @typedef {Object} ConversationServiceContract
 * @property {boolean} usingMock
 * @property {() => Promise<Array>} list
 * @property {(sessionId:string) => Promise<Object>} remove
 */

export const SERVICE_NAMES = [
  'CaseService', 'BundleService', 'ReminderService', 'DashboardService',
  'TimelineService', 'VerificationService', 'OCRService', 'SearchService', 'ConversationService',
];
