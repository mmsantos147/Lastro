import { useState } from 'react'
import { applyTheme, currentTheme, type Theme } from './theme'

export function ThemeToggle() {
  const [theme, setTheme] = useState<Theme>(currentTheme)
  const next: Theme = theme === 'dark' ? 'light' : 'dark'

  return (
    <button
      type="button"
      className="button button-ghost"
      onClick={() => {
        applyTheme(next)
        setTheme(next)
      }}
      aria-label={next === 'dark' ? 'Usar tema escuro' : 'Usar tema claro'}
    >
      {next === 'dark' ? 'Tema escuro' : 'Tema claro'}
    </button>
  )
}
