import { Link } from 'react-router'
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { useHistory, useSummary, type HistoryPoint, type PortfolioSummary } from '../api/portfolio'
import { ChartCard } from '../components/charts/ChartCard'
import { ChartTooltip } from '../components/charts/ChartTooltip'
import { axisProps, formatCompactMoney, formatMonth, gridProps, series } from '../components/charts/theme'
import { QueryState } from '../components/form'
import { StatTile } from '../components/StatTile'
import { formatDate, formatMoney, formatPercent, signClass } from '../lib/format'

const percentAxis = new Intl.NumberFormat('pt-BR', { style: 'percent', maximumFractionDigits: 0 })

export function OverviewPage() {
  const summary = useSummary()
  const history = useHistory()

  return (
    <section className="stack">
      <h1>Visão geral</h1>
      <QueryState isPending={summary.isPending} error={summary.error} />
      {summary.data && !summary.data.startDate && (
        <div className="card empty-state">
          <p>Nenhuma operação registrada ainda.</p>
          <Link to="/operacoes" className="button">
            Registrar a primeira operação
          </Link>
        </div>
      )}
      {summary.data?.startDate && <SummaryTiles summary={summary.data} />}
      {history.data && history.data.length > 0 && (
        <div className="grid-2">
          <WealthChart points={history.data} />
          <ReturnChart points={history.data} />
        </div>
      )}
    </section>
  )
}

function SummaryTiles({ summary }: { summary: PortfolioSummary }) {
  const gain = summary.marketValue - summary.invested
  return (
    <div className="tiles">
      <StatTile
        label="Patrimônio"
        value={formatMoney(summary.marketValue)}
        detail={
          <>
            Investido líquido {formatMoney(summary.invested)} ·{' '}
            <span className={signClass(gain)}>{formatMoney(gain)}</span>
          </>
        }
      />
      <StatTile
        label={`Rentabilidade desde ${formatDate(summary.startDate)}`}
        value={<span className={signClass(summary.returnSinceStart)}>{formatPercent(summary.returnSinceStart)}</span>}
        detail={benchmarks(summary.cdiSinceStart, summary.ibovSinceStart)}
      />
      <StatTile
        label="Rentabilidade em 12 meses"
        value={
          summary.return12Months === null ? (
            <span className="muted">—</span>
          ) : (
            <span className={signClass(summary.return12Months)}>{formatPercent(summary.return12Months)}</span>
          )
        }
        detail={
          summary.return12Months === null
            ? 'A carteira tem menos de 12 meses.'
            : benchmarks(summary.cdi12Months, summary.ibov12Months)
        }
      />
    </div>
  )
}

function benchmarks(cdi: number | null, ibov: number | null) {
  return `CDI ${formatPercent(cdi)} · Ibovespa ${ibov === null ? 'indisponível' : formatPercent(ibov)}`
}

function WealthChart({ points }: { points: HistoryPoint[] }) {
  return (
    <ChartCard
      title="Patrimônio"
      description="Valor de mercado e investido líquido (aportes menos vendas e proventos recebidos)."
      legend={[
        { label: 'Patrimônio', color: series[1], kind: 'line' },
        { label: 'Investido líquido', color: series[2], kind: 'line' },
      ]}
      table={
        <table>
          <thead>
            <tr>
              <th>Data</th>
              <th className="number">Patrimônio</th>
              <th className="number">Investido líquido</th>
            </tr>
          </thead>
          <tbody>
            {[...points].reverse().map((point) => (
              <tr key={point.date}>
                <td>{formatDate(point.date)}</td>
                <td className="number">{formatMoney(point.marketValue)}</td>
                <td className="number">{formatMoney(point.invested)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      }
    >
      <ResponsiveContainer width="100%" height={280}>
        <LineChart data={points} margin={{ top: 8, right: 8, bottom: 0, left: 8 }}>
          <CartesianGrid {...gridProps} />
          <XAxis dataKey="date" tickFormatter={formatMonth} minTickGap={32} {...axisProps} />
          <YAxis tickFormatter={formatCompactMoney} width={72} {...axisProps} axisLine={false} />
          <Tooltip
            cursor={{ stroke: 'var(--chart-axis)' }}
            content={(props) => <ChartTooltip {...props} formatLabel={formatDate} formatValue={(value) => formatMoney(value)} />}
          />
          <Line name="Patrimônio" dataKey="marketValue" stroke={series[1]} strokeWidth={2} dot={false} isAnimationActive={false} />
          <Line name="Investido líquido" dataKey="invested" stroke={series[2]} strokeWidth={2} dot={false} isAnimationActive={false} />
        </LineChart>
      </ResponsiveContainer>
    </ChartCard>
  )
}

/** Cumulative returns on one percentage axis: the time-weighted return is comparable with CDI and Ibovespa. */
function ReturnChart({ points }: { points: HistoryPoint[] }) {
  const hasIbov = points.some((point) => point.ibovIndex !== null)
  const data = points.map((point) => ({
    date: point.date,
    portfolio: point.portfolioIndex - 1,
    cdi: point.cdiIndex - 1,
    ibov: point.ibovIndex === null ? null : point.ibovIndex - 1,
  }))
  const legend = [
    { label: 'Carteira', color: series[1], kind: 'line' as const },
    { label: 'CDI', color: series[2], kind: 'line' as const },
    ...(hasIbov ? [{ label: 'Ibovespa', color: series[3], kind: 'line' as const }] : []),
  ]

  return (
    <ChartCard
      title="Rentabilidade acumulada"
      description={hasIbov ? 'Rentabilidade ponderada no tempo, sem o efeito dos aportes.' : 'Ibovespa indisponível: configure o token do brapi.'}
      legend={legend}
      table={
        <table>
          <thead>
            <tr>
              <th>Data</th>
              <th className="number">Carteira</th>
              <th className="number">CDI</th>
              {hasIbov && <th className="number">Ibovespa</th>}
            </tr>
          </thead>
          <tbody>
            {[...data].reverse().map((point) => (
              <tr key={point.date}>
                <td>{formatDate(point.date)}</td>
                <td className="number">{formatPercent(point.portfolio)}</td>
                <td className="number">{formatPercent(point.cdi)}</td>
                {hasIbov && <td className="number">{formatPercent(point.ibov)}</td>}
              </tr>
            ))}
          </tbody>
        </table>
      }
    >
      <ResponsiveContainer width="100%" height={280}>
        <LineChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 8 }}>
          <CartesianGrid {...gridProps} />
          <XAxis dataKey="date" tickFormatter={formatMonth} minTickGap={32} {...axisProps} />
          <YAxis tickFormatter={(value: number) => percentAxis.format(value)} width={56} {...axisProps} axisLine={false} />
          <Tooltip
            cursor={{ stroke: 'var(--chart-axis)' }}
            content={(props) => <ChartTooltip {...props} formatLabel={formatDate} formatValue={(value) => formatPercent(value)} />}
          />
          <Line name="Carteira" dataKey="portfolio" stroke={series[1]} strokeWidth={2} dot={false} isAnimationActive={false} />
          <Line name="CDI" dataKey="cdi" stroke={series[2]} strokeWidth={2} dot={false} isAnimationActive={false} />
          {hasIbov && (
            <Line name="Ibovespa" dataKey="ibov" stroke={series[3]} strokeWidth={2} dot={false} isAnimationActive={false} connectNulls />
          )}
        </LineChart>
      </ResponsiveContainer>
    </ChartCard>
  )
}
