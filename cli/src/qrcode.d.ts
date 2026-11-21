declare module 'qrcode/lib/core/qrcode.js' {
  interface QrMatrix {
    modules: { size: number; data: Uint8Array }
  }
  const QRCode: {
    create(text: string, options?: { errorCorrectionLevel?: 'L' | 'M' | 'Q' | 'H' }): QrMatrix
  }
  export default QRCode
}
