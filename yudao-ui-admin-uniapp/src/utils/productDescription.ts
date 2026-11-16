/** Read existing descriptions as ordinary text, on H5 and mini-program alike. */
export function descriptionToText(html = ''): string {
  const entities: Record<string, string> = { nbsp: ' ', amp: '&', lt: '<', gt: '>', quot: '"', apos: '\'' }
  return html
    .replace(/<(script|style)\b[^>]*>[\s\S]*?<\/\1\s*>/gi, '')
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/<br\b[^>]*>/gi, '\n')
    .replace(/<\/(?:p|div|h[1-6]|li|tr)\s*>/gi, '\n')
    .replace(/<\/?[a-z][^>]*>/gi, '')
    .replace(/&(#x[\da-f]+|#\d+|nbsp|amp|lt|gt|quot|apos);/gi, (match, entity: string) => {
      const key = entity.toLowerCase()
      if (!key.startsWith('#'))
        return entities[key] ?? match
      const code = key.startsWith('#x') ? Number.parseInt(key.slice(2), 16) : Number.parseInt(key.slice(1), 10)
      return code > 0 && code <= 0x10FFFF && !(code >= 0xD800 && code <= 0xDFFF) ? String.fromCodePoint(code) : match
    })
    .replace(/\r/g, '')
    .replace(/\n+$/, '')
}

/** Keep old embedded photos/media when the owner edits the text. */
export function descriptionFromText(text: string, originalHtml = ''): string {
  const media = originalHtml.match(/<img\b[^>]*>|<(?:video|audio)\b[^>]*>[\s\S]*?<\/(?:video|audio)\s*>/gi) || []
  if (!text.trim())
    return media.join('')
  const escaped = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  return escaped.split(/\r?\n/).map(line => `<p>${line || '<br>'}</p>`).join('') + media.join('')
}
