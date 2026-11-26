interface TooltipEntry {
  name?: string | number
  value?: unknown
  color?: string
  dataKey?: string | number | ((obj: unknown) => unknown)
}

interface ChartTooltipProps {
  active?: boolean
  payload?: readonly TooltipEntry[]
  label?: string | number
  formatLabel: (label: string) => string
  formatValue: (value: number) => string
}

/** Tooltip in the app's style: the label (e.g. the date) and one row per series with its swatch. */
export function ChartTooltip({ active, payload, label, formatLabel, formatValue }: ChartTooltipProps) {
  if (!active || !payload?.length) return null
  return (
    <div className="chart-tooltip">
      <p>
        <strong>{formatLabel(String(label))}</strong>
      </p>
      {payload
        .filter((entry) => typeof entry.value === 'number')
        .map((entry) => (
          <p key={String(entry.dataKey ?? entry.name)} className="tooltip-row">
            <span className="swatch" style={{ background: entry.color }} aria-hidden />
            {entry.name}
            <span className="tooltip-value">{formatValue(entry.value as number)}</span>
          </p>
        ))}
    </div>
  )
}
