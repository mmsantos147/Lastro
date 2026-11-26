import { useState, type ReactNode } from 'react'

export interface LegendItem {
  label: string
  color: string
  kind?: 'line' | 'box'
}

interface ChartCardProps {
  title: string
  description?: ReactNode
  /** Shown for two or more series, so identity never depends on color alone. */
  legend?: LegendItem[]
  /** Same data as a table: the accessible alternative to the chart. */
  table: ReactNode
  children: ReactNode
}

export function ChartCard({ title, description, legend, table, children }: ChartCardProps) {
  const [showTable, setShowTable] = useState(false)

  return (
    <section className="card chart-card" aria-label={title}>
      <div className="chart-header">
        <div>
          <h2>{title}</h2>
          {description && <p className="muted">{description}</p>}
        </div>
        <button type="button" className="link-button" onClick={() => setShowTable((current) => !current)}>
          {showTable ? 'Ver gráfico' : 'Ver tabela'}
        </button>
      </div>
      {legend && legend.length > 1 && !showTable && <ChartLegend items={legend} />}
      {showTable ? <div className="table-wrapper table-scroll">{table}</div> : children}
    </section>
  )
}

export function ChartLegend({ items }: { items: LegendItem[] }) {
  return (
    <ul className="chart-legend">
      {items.map((item) => (
        <li key={item.label}>
          <span
            className={item.kind === 'line' ? 'swatch swatch-line' : 'swatch'}
            style={{ background: item.color }}
            aria-hidden
          />
          {item.label}
        </li>
      ))}
    </ul>
  )
}
