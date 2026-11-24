import { Badge } from '@astryxdesign/core/Badge'
import { Button } from '@astryxdesign/core/Button'
import { Card } from '@astryxdesign/core/Card'
import { Heading } from '@astryxdesign/core/Heading'
import { Stack } from '@astryxdesign/core/Stack'
import { Text } from '@astryxdesign/core/Text'
import { TextInput } from '@astryxdesign/core/TextInput'
import { useState } from 'react'

/** Development only: the neutral Astryx theme on real components, with this product's content. */
export function AstryxPreview() {
  const [name, setName] = useState('Salem Todo DevOps')
  const [key, setKey] = useState('SALEM')
  return (
    <Stack padding={8} gap={6} maxWidth={960}>
      <Stack gap={1}>
        <Heading level={1}>Astryx preview · neutral theme</Heading>
        <Text color="secondary">Real Astryx components with this product&apos;s content.</Text>
      </Stack>

      <Stack direction="horizontal" gap={2}>
        <Button variant="primary" label="Create project" />
        <Button label="Cancel" />
        <Button variant="ghost" label="Show more" />
        <Button variant="destructive" label="Archive project" />
        <Button label="Disabled" isDisabled />
      </Stack>

      <Card padding={6} maxWidth={420}>
        <Stack gap={4}>
          <Heading level={2}>New project</Heading>
          <TextInput label="Project name" value={name} onChange={setName} />
          <TextInput
            label="Key"
            value={key}
            onChange={setKey}
            description="2 to 10 uppercase letters or digits, starting with a letter."
          />
          <Stack direction="horizontal" gap={2}>
            <Button variant="primary" label="Create project" />
            <Button label="Cancel" />
          </Stack>
        </Stack>
      </Card>

      <Stack direction="horizontal" gap={2}>
        <Badge label="In progress" variant="info" />
        <Badge label="Completed" variant="success" />
        <Badge label="Overdue" variant="error" />
        <Badge label="frontend" variant="blue" />
        <Badge label="backend" variant="green" />
      </Stack>
    </Stack>
  )
}
