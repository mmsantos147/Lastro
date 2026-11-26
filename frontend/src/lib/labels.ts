// Portuguese labels for the backend enums (codes stay in English in the API).

export const assetClassLabels = {
  STOCK: 'Ações',
  FII: 'FIIs',
  ETF: 'ETFs',
  TREASURY: 'Tesouro Direto',
  CRYPTO: 'Cripto',
  FIXED_INCOME: 'Renda fixa (CDB, LCI, LCA)',
} as const
export type AssetClass = keyof typeof assetClassLabels

export const fixedIncomeIndexerLabels = {
  CDI: '% do CDI',
  SELIC: '% da Selic',
  IPCA: 'IPCA + taxa',
  PREFIXED: 'Prefixado',
} as const
export type FixedIncomeIndexer = keyof typeof fixedIncomeIndexerLabels

export const institutionTypeLabels = {
  BROKER: 'Corretora',
  BANK: 'Banco',
  EXCHANGE: 'Exchange',
  WALLET: 'Carteira própria',
} as const
export type InstitutionType = keyof typeof institutionTypeLabels

export const tradeTypeLabels = { BUY: 'Compra', SELL: 'Venda' } as const
export type TradeType = keyof typeof tradeTypeLabels

export const distributionTypeLabels = {
  DIVIDEND: 'Dividendo',
  JCP: 'JCP',
  FII_INCOME: 'Rendimento de FII',
  FIXED_INCOME_INTEREST: 'Juros de renda fixa',
  STAKING_REWARD: 'Recompensa de staking',
} as const
export type DistributionType = keyof typeof distributionTypeLabels

export const corporateActionTypeLabels = {
  STOCK_SPLIT: 'Desdobramento',
  REVERSE_SPLIT: 'Grupamento',
  BONUS_SHARES: 'Bonificação',
  AMORTIZATION: 'Amortização',
  MERGER: 'Incorporação',
} as const
export type CorporateActionType = keyof typeof corporateActionTypeLabels

export const sourceLabels = {
  MANUAL: 'Manual',
  B3_IMPORT: 'Importação B3',
  BINANCE_API: 'Binance (API)',
  BINANCE_CSV: 'Binance (CSV)',
} as const
export type EntrySource = keyof typeof sourceLabels

export const importSourceLabels = {
  B3_IMPORT: 'B3 — movimentação (.xlsx)',
  BINANCE_CSV: 'Binance — histórico de transações (.csv ou .zip)',
} as const
export type ImportSource = keyof typeof importSourceLabels

export const importStatusLabels = {
  COMPLETED: 'Concluída',
  COMPLETED_WITH_ISSUES: 'Concluída com pendências',
} as const
export type ImportStatus = keyof typeof importStatusLabels

/** What each issue code means for the user, and what to do about it. */
export const importIssueLabels = {
  INFORMATIONAL: 'Sem efeito na posição (transferência de custódia, direitos, atualização, depósito em reais).',
  CORPORATE_EVENT_CASH: 'Dinheiro recebido em evento corporativo (leilão de fração, resgate em incorporação). Não é registrado.',
  FIXED_INCOME_TERMS_REQUIRED:
    'Renda fixa importada sem as condições (taxa, indexador, vencimento), que o arquivo não traz. Até você informá-las em Ativos e instituições, o título vale o preço pago.',
  UNSUPPORTED: 'Movimentação não suportada pela importação. Registre manualmente, se fizer sentido.',
  MERGER_REQUIRED:
    'Ativo recebido por incorporação. Cadastre a incorporação em Eventos corporativos, indicando o ativo de origem, e importe o arquivo de novo.',
  INCONSISTENT_HISTORY:
    'Deixaria o histórico inconsistente (ex.: venda maior que a posição). Geralmente falta uma incorporação ou uma operação anterior.',
  NO_POSITION_FOR_EVENT: 'Evento de um ativo que você não tinha na data. Ignorado.',
  UNKNOWN_ASSIGNED_COST: 'Bonificação registrada com custo atribuído zero: o arquivo não informa o custo. Ajuste se necessário.',
  PRICE_UNAVAILABLE: 'Conversão entre dois criptoativos sem preço em reais ou dólar: registrada com custo zero.',
  INVALID_ROW: 'Linha que não pôde ser lida.',
} as const
export type ImportIssueCode = keyof typeof importIssueLabels

export function options<T extends string>(labels: Record<T, string>): { value: T; label: string }[] {
  return (Object.keys(labels) as T[]).map((value) => ({ value, label: labels[value] }))
}

export const bucketStatusLabels = {
  BELOW: 'Abaixo do alvo',
  WITHIN: 'Dentro da faixa',
  ABOVE: 'Acima do alvo',
  NO_TARGET: 'Sem alvo',
} as const

export const suggestionReasonLabels = {
  BELOW_TARGET: 'Abaixo do alvo',
  PROPORTIONAL_TO_TARGET: 'Proporcional ao alvo (todas as classes no alvo)',
  NEW_POSITION_IN_CLASS: 'Classe sem ativos: escolha um ativo',
  ASSET_CAP_REACHED: 'Ativos da classe no limite: escolha um novo ativo',
} as const
