import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { SafeMarkdown } from './SafeMarkdown'

describe('SafeMarkdown', () => {
  it('renders markdown with tables', () => {
    render(<SafeMarkdown>{'## Leitura\n\n| Classe | Valor |\n|---|---|\n| FII | 10 |'}</SafeMarkdown>)

    expect(screen.getByRole('heading', { name: 'Leitura' })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: 'FII' })).toBeInTheDocument()
  })

  it('never renders raw HTML', () => {
    const { container } = render(
      <SafeMarkdown>{'Texto <script>alert(1)</script> <img src=x onerror="alert(2)"> <b>negrito</b>'}</SafeMarkdown>,
    )

    expect(container.querySelector('script')).toBeNull()
    expect(container.querySelector('img')).toBeNull()
    expect(container.querySelector('b')).toBeNull()
  })

  it('drops javascript: links and opens the others safely', () => {
    render(<SafeMarkdown>{'[ruim](javascript:alert(1)) e [fonte](https://example.com)'}</SafeMarkdown>)

    expect(screen.getByText('ruim').getAttribute('href') ?? '').not.toContain('javascript')
    const source = screen.getByRole('link', { name: 'fonte' })
    expect(source).toHaveAttribute('href', 'https://example.com')
    expect(source).toHaveAttribute('rel', 'noopener noreferrer nofollow')
  })

  it('does not load images written in markdown', () => {
    const { container } = render(<SafeMarkdown>{'![pixel](https://tracker.example/p.png)'}</SafeMarkdown>)

    expect(container.querySelector('img')).toBeNull()
    expect(screen.getByText('[imagem: pixel]')).toBeInTheDocument()
  })
})
