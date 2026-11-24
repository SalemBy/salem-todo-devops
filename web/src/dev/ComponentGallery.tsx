import { Avatar } from '@astryxdesign/core/Avatar'
import { Badge } from '@astryxdesign/core/Badge'
import { Button } from '@astryxdesign/core/Button'
import { Dialog, DialogHeader } from '@astryxdesign/core/Dialog'
import { DropdownMenu } from '@astryxdesign/core/DropdownMenu'
import { EmptyState } from '@astryxdesign/core/EmptyState'
import { Heading } from '@astryxdesign/core/Heading'
import { Selector } from '@astryxdesign/core/Selector'
import { Stack } from '@astryxdesign/core/Stack'
import { Text } from '@astryxdesign/core/Text'
import { TextInput } from '@astryxdesign/core/TextInput'
import { useToast } from '@astryxdesign/core/Toast'
import { useState, type ReactNode } from 'react'
import { ApiError } from '../api/client'
import { CatMark } from '../brand/CatMark'
import { CAT_POSES } from '../brand/catPoses'
import { labelVariant } from '../brand/labelVariant'
import { SalemCat } from '../brand/SalemCat'
import { ApiErrorBanner } from '../components/ApiErrorBanner'
import { formatDate } from '../format/date'

/** Development only: the Astryx components this app uses, in their states, with this product's content. */
export function ComponentGallery() {
  return (
    <Stack padding={10} gap={8} maxWidth={1100}>
      <Stack gap={1}>
        <Heading level={1}>Components</Heading>
        <Text color="secondary">
          Astryx (Neutral theme) with this product&apos;s content. Development builds only.
        </Text>
      </Stack>
      <Buttons />
      <Fields />
      <Overlays />
      <Marks />
      <Feedback />
      <Salem />
    </Stack>
  )
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Stack gap={3}>
      <Heading level={2}>{title}</Heading>
      {children}
    </Stack>
  )
}

function Buttons() {
  return (
    <Section title="Buttons">
      <Stack direction="horizontal" gap={2}>
        <Button variant="primary" label="Create project" />
        <Button label="Cancel" />
        <Button variant="ghost" label="Show more" />
        <Button variant="destructive" label="Archive project" />
        <Button variant="primary" label="Saving" isLoading />
        <Button label="Disabled" isDisabled />
      </Stack>
    </Section>
  )
}

function Fields() {
  const [name, setName] = useState('')
  const [key, setKey] = useState('salem')
  const [priority, setPriority] = useState('HIGH')
  return (
    <Section title="Fields">
      <Stack gap={4} maxWidth={420}>
        <TextInput label="Project name" value={name} onChange={setName} />
        <TextInput
          label="Key"
          value={key}
          onChange={setKey}
          description="2 to 10 uppercase letters or digits, starting with a letter. It cannot change later."
          status={{
            type: 'error',
            message: 'Use uppercase letters or digits, starting with a letter.',
          }}
        />
        <Selector
          label="Priority"
          value={priority}
          onChange={(value) => value && setPriority(value)}
          options={[
            { value: 'LOW', label: 'Low' },
            { value: 'MEDIUM', label: 'Medium' },
            { value: 'HIGH', label: 'High' },
            { value: 'CRITICAL', label: 'Critical' },
          ]}
        />
      </Stack>
    </Section>
  )
}

function Overlays() {
  const [open, setOpen] = useState(false)
  return (
    <Section title="Menu and dialog">
      <Stack direction="horizontal" gap={2}>
        <DropdownMenu
          button={{ label: 'Project actions' }}
          items={[
            { label: 'Edit details' },
            { label: 'Archive project', variant: 'destructive', onClick: () => setOpen(true) },
          ]}
        />
        <Button variant="destructive" label="Archive project" onClick={() => setOpen(true)} />
      </Stack>
      <Dialog isOpen={open} onOpenChange={setOpen}>
        <DialogHeader title="Archive Salem Todo DevOps?" onOpenChange={setOpen} />
        <Stack gap={4} padding={4}>
          <Text>
            Archived projects stay readable, but nobody can change their tasks, labels or members.
          </Text>
          <Stack direction="horizontal" gap={2} hAlign="end">
            <Button label="Cancel" onClick={() => setOpen(false)} />
            <Button variant="destructive" label="Archive project" onClick={() => setOpen(false)} />
          </Stack>
        </Stack>
      </Dialog>
    </Section>
  )
}

function Marks() {
  return (
    <Section title="Badges, labels and people">
      <Stack direction="horizontal" gap={2}>
        <Badge label="In progress" variant="info" />
        <Badge label="Completed" variant="success" />
        <Badge label="Overdue" variant="error" />
        {['frontend', 'backend', 'infra', 'design', 'docs', 'security'].map((name) => (
          <Badge key={name} label={name} variant={labelVariant(name)} />
        ))}
      </Stack>
      <Stack direction="horizontal" gap={3} vAlign="center">
        <Avatar name="Ada Lovelace" />
        <Avatar name="Grace Hopper" size="sm" />
        <Text color="secondary">Due {formatDate('2026-12-11')}</Text>
      </Stack>
    </Section>
  )
}

function Feedback() {
  const showToast = useToast()
  return (
    <Section title="Empty, error and toast">
      <EmptyState
        title="No projects yet"
        description="Salem is napping until there is work to do."
        icon={<SalemCat pose="asleep" size={120} />}
        actions={<Button variant="primary" label="Create project" />}
      />
      <Stack gap={3} maxWidth={520}>
        <ApiErrorBanner error="The server could not be reached. Check your connection." />
        <ApiErrorBanner
          error={
            new ApiError(400, 'Bad Request', 'Request validation failed', [
              { field: 'key', message: 'Key is already in use' },
              { field: 'name', message: 'Name is required' },
            ])
          }
        />
      </Stack>
      <Stack direction="horizontal" gap={2}>
        <Button label="Show a toast" onClick={() => showToast({ body: 'Task archived' })} />
        <Button
          label="Show an error toast"
          onClick={() =>
            showToast({ body: 'Move failed: this project is archived.', type: 'error' })
          }
        />
      </Stack>
    </Section>
  )
}

function Salem() {
  return (
    <Section title="Salem">
      <Stack direction="horizontal" gap={4} vAlign="center">
        <CatMark size={16} />
        <CatMark size={20} />
        <CatMark size={32} label="Salem Todo DevOps" />
        {CAT_POSES.map((pose) => (
          <SalemCat key={pose} pose={pose} />
        ))}
      </Stack>
    </Section>
  )
}
