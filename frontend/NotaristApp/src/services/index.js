// Service registry (Sprint 3, Task 6). The single import site for the domain services. Screens and
// state slices import from here — never from api/* directly — so the mock→HTTP swap is invisible to
// them (it happens inside api/*, gated by FEATURES). See services/contracts.js for the interfaces.
export { CaseService } from './CaseService';
export { BundleService } from './BundleService';
export { ReminderService } from './ReminderService';
export { DashboardService } from './DashboardService';
export { TimelineService } from './TimelineService';
export { VerificationService } from './VerificationService';
export { OCRService } from './OCRService';
export { SearchService } from './SearchService';
export { ConversationService } from './ConversationService';

// Pre-existing services kept in the registry for a single discovery point.
export { RegisterService, EndpointUnavailableError } from './RegisterService';
export { NotificationService, notificationService } from './NotificationService';
