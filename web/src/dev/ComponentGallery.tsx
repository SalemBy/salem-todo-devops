import { useState } from 'react'
import { ApiError } from '../api/client'
import { Avatar } from '../components/Avatar/Avatar'
import { Badge } from '../components/Badge/Badge'
import { Button } from '../components/Button/Button'
import { Dialog } from '../components/Dialog/Dialog'
import { EmptyState } from '../components/EmptyState/EmptyState'
import { ErrorMessage } from '../components/ErrorMessage/ErrorMessage'
import { LabelChip } from '../components/LabelChip/LabelChip'
import { Menu } from '../components/Menu/Menu'
import { Select } from '../components/Select/Select'
import { TextField } from '../components/TextField/TextField'
import { ToastProvider } from '../components/Toast/Toast'
import { useToast } from '../components/Toast/useToast'
import { formatDate } from '../format/date'
import styles from './ComponentGallery.module.css'

/** Development only: every base component in its states, for design critique and accessibility review. */
export function ComponentGallery() {
  return (
    <ToastProvider>
      <main className={styles.page}>
        <h1 className={styles.heading}>Components</h1>
        <p className={styles.lead}>
          Every base component of the web UI in its states. Development builds only.
        </p>
        <Buttons />
        <Fields />
        <Overlays />
        <Marks />
        <Feedback />
      </main>
    </ToastProvider>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className={styles.section}>
      <h2 className={styles.title}>{title}</h2>
      <div className={styles.row}>{children}</div>
    </section>
  )
}

function Buttons() {
  return (
    <Section title="Buttons">
      <Button variant="primary">Create project</Button>
      <Button>Cancel</Button>
      <Button variant="ghost">Show more</Button>
      <Button variant="danger">Archive project</Button>
      <Button variant="primary" loading>
        Saving
      </Button>
      <Button disabled>Disabled</Button>
    </Section>
  )
}

function Fields() {
  const [priority, setPriority] = useState('HIGH')
  return (
    <Section title="Fields">
      <div className={styles.stack}>
        <TextField label="Project name" placeholder="Salem Todo DevOps" />
        <TextField
          label="Key"
          defaultValue="SALEM"
          hint="2 to 10 uppercase letters or digits, starting with a letter. It cannot change later."
        />
        <TextField
          label="Key"
          defaultValue="salem"
          error="Key must contain 2 to 10 uppercase letters or digits and start with a letter"
        />
        <TextField label="Description" disabled defaultValue="Read only in archived projects" />
      </div>
      <div className={styles.stack}>
        <Select
          label="Priority"
          value={priority}
          onValueChange={setPriority}
          options={[
            { value: 'LOW', label: 'Low' },
            { value: 'MEDIUM', label: 'Medium' },
            { value: 'HIGH', label: 'High' },
            { value: 'CRITICAL', label: 'Critical' },
          ]}
        />
        <Select
          label="Assignee"
          value={undefined}
          onValueChange={() => {}}
          placeholder="Unassigned"
          options={[]}
        />
      </div>
    </Section>
  )
}

function Overlays() {
  const [open, setOpen] = useState(false)
  return (
    <Section title="Menu and dialog">
      <Menu
        label="Project actions"
        items={[
          { label: 'Edit details', onSelect: () => {} },
          { label: 'Archive project', onSelect: () => setOpen(true), tone: 'danger' },
        ]}
      />
      <Button variant="danger" onClick={() => setOpen(true)}>
        Archive project
      </Button>
      <Dialog
        open={open}
        onOpenChange={setOpen}
        title="Archive Salem Todo DevOps?"
        description="Archived projects stay readable, but nobody can change their tasks, labels or members."
        footer={
          <>
            <Button onClick={() => setOpen(false)}>Cancel</Button>
            <Button variant="danger" onClick={() => setOpen(false)}>
              Archive project
            </Button>
          </>
        }
      />
    </Section>
  )
}

function Marks() {
  return (
    <Section title="Badges, labels and people">
      <Badge tone="accent">Owner</Badge>
      <Badge>Viewer</Badge>
      <Badge tone="danger">Archived</Badge>
      <Badge tone="success">Completed</Badge>
      {['frontend', 'backend', 'infra', 'bug-bash', 'design', 'docs', 'security', 'release'].map(
        (name) => (
          <LabelChip key={name} name={name} />
        ),
      )}
      <Avatar name="Ada Lovelace" />
      <Avatar name="Grace Hopper" size="sm" />
      <span className={styles.date}>Due {formatDate('2026-12-11')}</span>
      <span className={styles.date}>Created {formatDate('2025-03-02T10:15:00Z')}</span>
    </Section>
  )
}

function Feedback() {
  const toast = useToast()
  return (
    <Section title="Empty, error and toast">
      <EmptyState
        title="No projects yet"
        body="Create a project to start planning work."
        action={<Button variant="primary">Create project</Button>}
      />
      <div className={styles.stack}>
        <ErrorMessage error="The server could not be reached. Check your connection." />
        <ErrorMessage
          error={
            new ApiError(400, 'Bad Request', 'Request validation failed', [
              { field: 'key', message: 'Key is already in use' },
              { field: 'name', message: 'Name is required' },
            ])
          }
        />
      </div>
      <Button onClick={() => toast.show({ title: 'Task archived', tone: 'success' })}>
        Show a toast
      </Button>
      <Button
        onClick={() =>
          toast.show({
            title: 'Move failed',
            description: 'This project is archived. Its tasks cannot change.',
            tone: 'danger',
          })
        }
      >
        Show an error toast
      </Button>
    </Section>
  )
}
