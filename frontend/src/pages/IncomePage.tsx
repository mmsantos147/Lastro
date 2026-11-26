import { useState } from 'react'
import { Bar, CartesianGrid, ComposedChart, Line, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { useIncome } from '../api/portfolio'
import { ChartCard } from '../components/charts/ChartCard'
import { ChartTooltip } from '../components/charts/ChartTooltip'
import { axisProps, formatCompactMoney, formatMonth, gridProps, series } from '../components/charts/theme'
import { QueryState } from '../components/form'
import { StatTile } from '../components/StatTile'
import { formatMoney, formatPercent } from '../lib/format'

const periods = [12, 24, 60] as const

export function IncomePage() {
  const [months, setMonths] = useState<(typeof periods)[number]>(24)
  const income = useIncome(months)

  return (
    <section className="stack">
      <div className="page-header">
        <h1>Proventos</h1>
        <label className="field field-inline">
          <span>Período</span>
          <select value={months} onChange={(event) => setMonths(Number(event.target.value) as (typeof periods)[number])}>
            {periods.map((period) => (
              <option key={period} value={period}>
                {period / 12 === 1 ? '12 meses' : `${period / 12} anos`}
              </option>
            ))}
          </select>
        </label>
      </div>
      <QueryState isPending={income.isPending} error={income.error} />
      {income.data && (
        <>
          <div className="tiles">
            <StatTile label="Recebido em 12 meses" value={formatMoney(income.data.lastTwelveMonths)} detail="Líquido de IR" />
            <StatTile label="Média mensal (12 meses)" value={formatMoney(income.data.lastTwelveMonths / 12)} />
            <StatTile
              label="Yield on cost (12 meses)"
              value={formatPercent(income.data.yieldOnCost)}
              detail="Proventos de 12 meses sobre o custo das posições atuais"
            />
          </div>
          <ChartCard
            title="Proventos por mês"
            description="Valores líquidos em reais, com a média móvel de 12 meses."
            legend={[
              { label: 'Recebido no mês', color: series[1] },
              { label: 'Média móvel 12 meses', color: series[2], kind: 'line' },
            ]}
            table={
              <table>
                <thead>
                  <tr>
                    <th>Mês</th>
                    <th className="number">Recebido</th>
                    <th className="number">Média 12 meses</th>
                  </tr>
                </thead>
                <tbody>
                  {[...income.data.months].reverse().map((month) => (
                    <tr key={month.month}>
                      <td>{formatMonth(month.month)}</td>
                      <td className="number">{formatMoney(month.net)}</td>
                      <td className="number">{formatMoney(month.movingAverage)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            }
          >
            <ResponsiveContainer width="100%" height={280}>
              <ComposedChart data={income.data.months} margin={{ top: 8, right: 8, bottom: 0, left: 8 }} barCategoryGap={2}>
                <CartesianGrid {...gridProps} />
                <XAxis dataKey="month" tickFormatter={formatMonth} minTickGap={24} {...axisProps} />
                <YAxis tickFormatter={formatCompactMoney} width={72} {...axisProps} axisLine={false} />
                <Tooltip
                  cursor={{ fill: 'var(--chart-grid)' }}
                  content={(props) => (
                    <ChartTooltip {...props} formatLabel={formatMonth} formatValue={(value) => formatMoney(value)} />
                  )}
                />
                <Bar name="Recebido no mês" dataKey="net" fill={series[1]} radius={[4, 4, 0, 0]} isAnimationActive={false} />
                <Line
                  name="Média móvel 12 meses"
                  dataKey="movingAverage"
                  stroke={series[2]}
                  strokeWidth={2}
                  dot={false}
                  isAnimationActive={false}
                />
              </ComposedChart>
            </ResponsiveContainer>
          </ChartCard>
          {income.data.byAsset.length > 0 && (
            <section className="stack">
              <h2>Por ativo (12 meses)</h2>
              <div className="table-wrapper">
                <table>
                  <thead>
                    <tr>
                      <th>Ativo</th>
                      <th className="number">Recebido</th>
                      <th className="number">Yield on cost</th>
                    </tr>
                  </thead>
                  <tbody>
                    {income.data.byAsset.map((asset) => (
                      <tr key={asset.assetId}>
                        <td>{asset.code}</td>
                        <td className="number">{formatMoney(asset.lastTwelveMonths)}</td>
                        <td className="number">{asset.yieldOnCost === null ? 'posição encerrada' : formatPercent(asset.yieldOnCost)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          )}
        </>
      )}
    </section>
  )
}
