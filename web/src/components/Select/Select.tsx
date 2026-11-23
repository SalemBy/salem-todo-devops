import { Check, ChevronDown } from 'lucide-react'
import { Select as Radix } from 'radix-ui'
import { useId } from 'react'
import styles from './Select.module.css'

export interface SelectOption {
  value: string
  label: string
}

export interface SelectProps {
  label: string
  options: SelectOption[]
  value: string | undefined
  onValueChange: (value: string) => void
  placeholder?: string
  disabled?: boolean
}

export function Select({
  label,
  options,
  value,
  onValueChange,
  placeholder,
  disabled,
}: SelectProps) {
  const id = useId()
  return (
    <div className={styles.field}>
      <label className={styles.label} htmlFor={id}>
        {label}
      </label>
      <Radix.Root value={value} onValueChange={onValueChange} disabled={disabled}>
        <Radix.Trigger id={id} className={styles.trigger}>
          <Radix.Value placeholder={placeholder} />
          <Radix.Icon className={styles.icon}>
            <ChevronDown size={14} aria-hidden="true" />
          </Radix.Icon>
        </Radix.Trigger>
        <Radix.Portal>
          <Radix.Content className={styles.content} position="popper" sideOffset={4}>
            <Radix.Viewport>
              {options.map((option) => (
                <Radix.Item key={option.value} value={option.value} className={styles.item}>
                  <Radix.ItemText>{option.label}</Radix.ItemText>
                  <Radix.ItemIndicator className={styles.check}>
                    <Check size={14} aria-hidden="true" />
                  </Radix.ItemIndicator>
                </Radix.Item>
              ))}
            </Radix.Viewport>
          </Radix.Content>
        </Radix.Portal>
      </Radix.Root>
    </div>
  )
}
