import { useId, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes } from 'react'
import { errorMessage, ApiError } from '../api/http'

interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  hint?: ReactNode
}

/** The hint stays outside the label (linked by aria-describedby), so the accessible name is just the label. */
export function Field({ label, hint, id, ...input }: FieldProps) {
  const generatedId = useId()
  const inputId = id ?? generatedId
  const hintId = `${inputId}-hint`
  return (
    <div className="field">
      <label htmlFor={inputId}>{label}</label>
      <input id={inputId} aria-describedby={hint ? hintId : undefined} {...input} />
      {hint && (
        <small id={hintId} className="muted">
          {hint}
        </small>
      )}
    </div>
  )
}

/** Text input for numbers: accepts Brazilian notation ("1.234,56"), parsed with parseDecimal. */
export function DecimalField(props: FieldProps) {
  return <Field inputMode="decimal" autoComplete="off" {...props} />
}

interface SelectFieldProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: string
  options: { value: string | number; label: string }[]
  placeholder?: string
}

export function SelectField({ label, options, placeholder, ...select }: SelectFieldProps) {
  return (
    <label className="field">
      <span>{label}</span>
      <select {...select}>
        {placeholder !== undefined && <option value="">{placeholder}</option>}
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </label>
  )
}

/** Error of a failed request, in Portuguese, with the backend detail as a hint. */
export function RequestError({ error }: { error: unknown }) {
  if (!error) return null
  const detail = error instanceof ApiError ? error.detail : undefined
  return (
    <p role="alert" className="error">
      {errorMessage(error)}
      {detail && <small className="error-detail">{detail}</small>}
    </p>
  )
}

export function QueryState({ isPending, error }: { isPending: boolean; error: unknown }) {
  if (isPending) return <p className="status">Carregando…</p>
  if (error) return <RequestError error={error} />
  return null
}
