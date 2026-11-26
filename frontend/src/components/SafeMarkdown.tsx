import Markdown, { type Components } from 'react-markdown'
import remarkGfm from 'remark-gfm'

// Links written by the LLM open in a new tab without access to this page.
const components: Components = {
  a: ({ href, children }) => (
    <a href={href} target="_blank" rel="noopener noreferrer nofollow">
      {children}
    </a>
  ),
  // Images could be used to track the reader or leak data in the URL: show the alt text instead.
  img: ({ alt }) => <span className="muted">[imagem: {alt}]</span>,
  table: ({ children }) => (
    <div className="table-wrapper">
      <table>{children}</table>
    </div>
  ),
}

/**
 * Renders untrusted markdown (e.g. analyses written by Claude). Raw HTML is never rendered: react-markdown escapes
 * it unless a raw-HTML plugin is added, which must not be done here. Dangerous URLs (javascript:) are dropped by
 * react-markdown's default URL transform. GFM adds tables and strikethrough.
 */
export function SafeMarkdown({ children }: { children: string }) {
  return (
    <div className="markdown">
      <Markdown remarkPlugins={[remarkGfm]} components={components} skipHtml>
        {children}
      </Markdown>
    </div>
  )
}
