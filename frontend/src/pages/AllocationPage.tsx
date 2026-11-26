import { Bar, BarChart, CartesianGrid, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { usePositions, type Position } from '../api/portfolio'
import { useAllocation, type Allocation } from '../api/strategy'
import { ChartCard, ChartLegend } from '../components/charts/ChartCard'
import { assetClassColors, axisProps, gridProps, series } from '../components/charts/theme'
import { ChartTooltip } from '../components/charts/ChartTooltip'
import { QueryState } from '../components/form'
import { formatMoney, formatPercent } from '../lib/format'
import { allocationByClass, type ClassSlice } from '../lib/allocation'
import { assetClassLabels, bucketStatusLabels } from '../lib/labels'
import { Link } from 'react-router'

export function AllocationPage() {
  const positions = usePositions()
  const allocation = useAllocation()

  return (
    <section className="stack">
      <h1>Alocação</h1>
      <QueryState isPending={positions.isPending} error={positions.error} />
      {allocation.data && allocation.data.buckets.length > 0 && <TargetComparison allocation={allocation.data} />}
      {positions.data?.length === 0 && <p className="muted">Nenhuma posição aberta.</p>}
      {positions.data && positions.data.length > 0 && (
        <div className="grid-2">
          <ClassAllocation positions={positions.data} />
          <AssetAllocation positions={positions.data} />
        </div>
      )}
    </section>
  )
}

function ClassAllocation({ positions }: { positions: Position[] }) {
  const slices = allocationByClass(positions)
  const table = (
    <table>
      <thead>
        <tr>
          <th>Classe</th>
          <th className="number">Valor</th>
          <th className="number">Atual</th>
        </tr>
      </thead>
      <tbody>
        {slices.map((slice) => (
          <tr key={slice.assetClass}>
            <td>{assetClassLabels[slice.assetClass]}</td>
            <td className="number">{formatMoney(slice.value)}</td>
            <td className="number">{formatPercent(slice.weight)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )

  return (
    <ChartCard title="Por classe" description="Valor de mercado (ou custo, sem cotação)." table={table}>
      <ResponsiveContainer width="100%" height={240}>
        <PieChart>
          <Pie
            data={slices}
            dataKey="value"
            nameKey="assetClass"
            innerRadius="60%"
            outerRadius="90%"
            paddingAngle={slices.length > 1 ? 1 : 0}
            stroke="var(--surface)"
            strokeWidth={2}
            isAnimationActive={false}
          >
            {slices.map((slice) => (
              <Cell key={slice.assetClass} fill={assetClassColors[slice.assetClass]} />
            ))}
          </Pie>
          <Tooltip
            content={({ active, payload }) => {
              const slice = payload?.[0]?.payload as ClassSlice | undefined
              if (!active || !slice) return null
              return (
                <div className="chart-tooltip">
                  <p>
                    <strong>{assetClassLabels[slice.assetClass]}</strong>
                  </p>
                  <p>
                    {formatMoney(slice.value)} · {formatPercent(slice.weight)}
                  </p>
                </div>
              )
            }}
          />
        </PieChart>
      </ResponsiveContainer>
      {/* Legend with values: identity never depends on color, and light slots below 3:1 get a visible label */}
      <ChartLegend
        items={slices.map((slice) => ({
          label: `${assetClassLabels[slice.assetClass]} ${formatPercent(slice.weight)}`,
          color: assetClassColors[slice.assetClass],
        }))}
      />
    </ChartCard>
  )
}

function AssetAllocation({ positions }: { positions: Position[] }) {
  const data = [...positions].sort((a, b) => b.weight - a.weight)
  // Room for the longest code on the axis (about 7px per character at 12px)
  const axisWidth = Math.min(180, Math.max(60, ...data.map((position) => position.code.length * 7.5 + 12)))
  const table = (
    <table>
      <thead>
        <tr>
          <th>Ativo</th>
          <th className="number">Valor</th>
          <th className="number">Peso</th>
        </tr>
      </thead>
      <tbody>
        {data.map((position) => (
          <tr key={position.assetId}>
            <td>{position.code}</td>
            <td className="number">{formatMoney(position.marketValue)}</td>
            <td className="number">{formatPercent(position.weight)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )

  return (
    <ChartCard title="Por ativo" description="Peso de cada ativo no patrimônio." table={table}>
      <ResponsiveContainer width="100%" height={Math.max(160, data.length * 28 + 24)}>
        <BarChart data={data} layout="vertical" margin={{ top: 0, right: 48, bottom: 0, left: 8 }} barCategoryGap={4}>
          <XAxis type="number" hide domain={[0, 'dataMax']} />
          <YAxis type="category" dataKey="code" width={axisWidth} {...axisProps} axisLine={false} />
          <Tooltip
            cursor={{ fill: 'var(--chart-grid)' }}
            content={({ active, payload }) => {
              const position = payload?.[0]?.payload as Position | undefined
              if (!active || !position) return null
              return (
                <div className="chart-tooltip">
                  <p>
                    <strong>{position.code}</strong> <span className="muted">{position.name}</span>
                  </p>
                  <p>
                    {formatMoney(position.marketValue)} · {formatPercent(position.weight)}
                  </p>
                </div>
              )
            }}
          />
          <Bar
            dataKey="weight"
            fill={series[1]}
            radius={[0, 4, 4, 0]}
            isAnimationActive={false}
            label={{ position: 'right', fill: 'var(--muted)', fontSize: 12, formatter: (value: unknown) => formatPercent(Number(value)) }}
          />
        </BarChart>
      </ResponsiveContainer>
    </ChartCard>
  )
}

const percentNumber = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 })

/** Current vs target per bucket of the active strategy, on one percentage axis. */
function TargetComparison({ allocation }: { allocation: Allocation }) {
  if (!allocation.strategy) {
    return (
      <p className="card">
        Sem estratégia ativa: não há alocação alvo para comparar. <Link to="/estrategia">Cadastrar estratégia</Link>
      </p>
    )
  }
  const data = allocation.buckets.map((bucket) => ({
    label: bucket.assetCode ?? (bucket.assetClass ? assetClassLabels[bucket.assetClass] : '—'),
    current: bucket.currentPercentage,
    target: bucket.targetPercentage,
    bucket,
  }))
  const table = (
    <table>
      <thead>
        <tr>
          <th>Alvo</th>
          <th className="number">Valor</th>
          <th className="number">Atual</th>
          <th className="number">Alvo</th>
          <th className="number">Desvio</th>
          <th>Situação</th>
        </tr>
      </thead>
      <tbody>
        {data.map(({ label, bucket }) => (
          <tr key={label}>
            <td>{label}</td>
            <td className="number">{formatMoney(bucket.value)}</td>
            <td className="number">{percentNumber.format(bucket.currentPercentage)}%</td>
            <td className="number">{percentNumber.format(bucket.targetPercentage)}%</td>
            <td className="number">
              {bucket.deviation > 0 ? '+' : ''}
              {percentNumber.format(bucket.deviation)} p.p.
            </td>
            <td>{bucketStatusLabels[bucket.status]}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )

  return (
    <ChartCard
      title={`Atual × alvo — ${allocation.strategy.name}`}
      description="Percentual do patrimônio em cada alvo da estratégia ativa."
      legend={[
        { label: 'Atual', color: series[1] },
        { label: 'Alvo', color: series[2] },
      ]}
      table={table}
    >
      <ResponsiveContainer width="100%" height={260}>
        <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 8 }} barGap={2} barCategoryGap="25%">
          <CartesianGrid {...gridProps} />
          <XAxis dataKey="label" {...axisProps} />
          <YAxis tickFormatter={(value: number) => `${value}%`} width={48} {...axisProps} axisLine={false} />
          <Tooltip
            cursor={{ fill: 'var(--chart-grid)' }}
            content={(props) => (
              <ChartTooltip {...props} formatLabel={(label) => label} formatValue={(value) => `${percentNumber.format(value)}%`} />
            )}
          />
          <Bar name="Atual" dataKey="current" fill={series[1]} radius={[4, 4, 0, 0]} isAnimationActive={false} />
          <Bar name="Alvo" dataKey="target" fill={series[2]} radius={[4, 4, 0, 0]} isAnimationActive={false} />
        </BarChart>
      </ResponsiveContainer>
    </ChartCard>
  )
}
