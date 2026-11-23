// Written by hand from the API's Java DTOs: the API publishes no OpenAPI spec.
// Instants are ISO-8601 strings, LocalDates are yyyy-MM-dd strings, and IDs are numbers.

export type TaskStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED'
export type TaskType = 'TASK' | 'BUG' | 'FEATURE' | 'ENHANCEMENT' | 'CHORE'
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type ProjectRole = 'OWNER' | 'MANAGER' | 'MEMBER' | 'VIEWER'
export type ResourceType = 'PROJECT' | 'TASK' | 'MEMBER' | 'COMMENT' | 'LABEL'
export type ActivityAction = 'CREATED' | 'UPDATED' | 'ARCHIVED' | 'ADDED' | 'REMOVED' | 'DELETED'

/** GET /auth/csrf */
export interface CsrfResponse {
  token: string
  headerName: string
}

/** identity AccountResponse */
export interface AccountResponse {
  id: number
  email: string
  displayName: string
  active: boolean
  createdAt: string
  updatedAt: string
  emailVerified: boolean
}

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface ProjectResponse {
  id: number
  name: string
  key: string
  description: string | null
  active: boolean
  createdAt: string
  updatedAt: string
  createdByAccountId: number
  /** The caller's role in this project. */
  role: ProjectRole
}

export interface LabelResponse {
  id: number
  projectId: number
  name: string
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface TaskResponse {
  id: number
  projectId: number
  title: string
  description: string | null
  status: TaskStatus
  createdAt: string
  updatedAt: string
  active: boolean
  type: TaskType
  priority: TaskPriority
  dueDate: string | null
  labels: LabelResponse[]
  assigneeId: number | null
}

export interface CommentResponse {
  id: number
  taskId: number
  authorId: number
  body: string
  createdAt: string
  updatedAt: string
}

export interface MemberResponse {
  accountId: number
  displayName: string
  email: string
  role: ProjectRole
  joinedAt: string
  updatedAt: string
}

export interface ChangeResponse {
  field: string
  /** Null for long text (descriptions, comment bodies): only the fact that it changed is recorded. */
  before: string | null
  after: string | null
}

export interface ActivityResponse {
  id: number
  projectId: number
  taskId: number | null
  actorId: number
  resourceType: ResourceType
  resourceId: number
  action: ActivityAction
  occurredAt: string
  changes: ChangeResponse[]
}

// Requests

export interface AccountRegistration {
  email: string
  displayName: string
  password: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface VerifyEmailRequest {
  token: string
  password: string
}

export interface VerificationEmailRequest {
  email: string
}

export interface PasswordChangeRequest {
  currentPassword: string
  newPassword: string
}

export interface CreateProjectRequest {
  name: string
  /** 2 to 10 uppercase letters or digits, starting with a letter. Cannot change later. */
  key: string
  description?: string | null
}

export interface UpdateProjectRequest {
  name: string
  description?: string | null
}

/** Fields left out keep their current value on update; dueDate: null clears the date. */
export interface TaskMetadata {
  type?: TaskType
  priority?: TaskPriority
  dueDate?: string | null
  labelIds?: number[]
}

export interface CreateTaskRequest extends TaskMetadata {
  title: string
  description?: string | null
}

export interface UpdateTaskRequest extends TaskMetadata {
  title: string
  description?: string | null
  status: TaskStatus
}

export interface AssignTaskRequest {
  accountId: number
}

export interface CommentRequest {
  body: string
}

export interface LabelRequest {
  name: string
}

export interface AddMemberRequest {
  email: string
  role: ProjectRole
}

export interface ChangeMemberRoleRequest {
  role: ProjectRole
}
