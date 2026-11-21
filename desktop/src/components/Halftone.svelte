<!--
  Nod's own procedural halftone band: a grid of dots whose size fades out
  across the band, with a little deterministic jitter. Decorative, used once
  to frame the empty state.
-->
<script lang="ts">
  let {
    width = 720,
    height = 72,
    step = 6,
  }: { width?: number; height?: number; step?: number } = $props()

  /** Small deterministic PRNG so the pattern is stable between renders. */
  const random = (seed: number) => () => {
    seed = (seed * 1664525 + 1013904223) % 4294967296
    return seed / 4294967296
  }

  const dots = $derived.by(() => {
    const next = random(7)
    const out: { x: number; y: number; r: number }[] = []
    for (let y = step / 2; y < height; y += step) {
      for (let x = step / 2; x < width; x += step) {
        // Density falls off towards the bottom and wobbles along the band.
        const wave = 0.5 + 0.5 * Math.sin(x / 53) * Math.cos(x / 131)
        const density = (1 - y / height) * (0.35 + 0.65 * wave)
        const r = (step / 2) * Math.min(1, density * 1.2) * (0.75 + 0.25 * next())
        if (r > 0.45) out.push({ x, y, r })
      }
    }
    return out
  })
</script>

<svg
  class="halftone"
  viewBox="0 0 {width} {height}"
  preserveAspectRatio="xMidYMin slice"
  aria-hidden="true"
>
  {#each dots as d, i (i)}<circle cx={d.x} cy={d.y} r={d.r.toFixed(2)} />{/each}
</svg>

<style>
  .halftone {
    display: block;
    width: 100%;
    height: 72px;
    fill: color-mix(in srgb, var(--c-primary) 22%, transparent);
  }
</style>
