export function DeleteButton({ onConfirm, disabled }: { onConfirm: () => void; disabled?: boolean }) {
  return (
    <button
      type="button"
      className="button button-ghost button-small"
      disabled={disabled}
      onClick={() => {
        if (window.confirm('Excluir este registro?')) onConfirm()
      }}
    >
      Excluir
    </button>
  )
}
