/** Renders a QR code with Unicode half blocks (two modules per character row). */
// Deep import: only the encoder, without the PNG/SVG renderers and their fs usage.
import QRCode from 'qrcode/lib/core/qrcode.js'

export const renderQr = (text: string): string => {
  const { modules } = QRCode.create(text, { errorCorrectionLevel: 'M' })
  const quiet = 2
  const size = modules.size
  const dark = (x: number, y: number) =>
    x >= 0 && y >= 0 && x < size && y < size && modules.data[y * size + x] === 1

  const lines: string[] = []
  for (let y = -quiet; y < size + quiet; y += 2) {
    let line = ''
    for (let x = -quiet; x < size + quiet; x++) {
      const top = dark(x, y)
      const bottom = dark(x, y + 1)
      // Light-on-dark terminals: print light modules as blocks so phones read it as dark-on-light.
      line += top ? (bottom ? ' ' : '▄') : bottom ? '▀' : '█'
    }
    lines.push(line)
  }
  return lines.join('\n')
}
