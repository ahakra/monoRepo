import { useEffect, useRef } from 'react'

// Decorative 3D-ish graph canvas for graph database / ETL visualization.
export default function NetworkScene({ paused }) {
  const canvasRef = useRef(null)

  useEffect(() => {
    const canvas = canvasRef.current
    const context = canvas?.getContext('2d')
    if (!canvas || !context) return undefined

    const motion = window.matchMedia('(prefers-reduced-motion: reduce)')
    const pointer = { x: 0, y: 0, active: false, strength: 0 }

    const NODE_COUNT = 88
    const MAX_EDGES_PER_NODE = 4
    const camera = 920

    // Create nodes distributed in 3D space.
    const nodes = Array.from({ length: NODE_COUNT }, (_, index) => {
      const t = index / NODE_COUNT
      const theta = index * 2.399963229728653 // golden angle
      const radius = 0.2 + Math.sqrt(t) * 0.92
      const band = Math.sin(index * 0.73) * 0.5 + 0.5

      return {
        id: index,
        theta,
        radius,
        band,
        phase: index * 1.73,
        depthBias: ((index * 17) % 31) / 31,
        bob: ((index * 11) % 19) / 19,
        // 3D world
        wx: 0,
        wy: 0,
        wz: 0,
        // projected 2D
        x: 0,
        y: 0,
        scale: 1,
        // interaction
        pullX: 0,
        pullY: 0,
        attachment: 0,
      }
    })

    // Build stable topology from 3D-ish base layout.
    const candidates = []
    for (let i = 0; i < nodes.length; i += 1) {
      for (let j = i + 1; j < nodes.length; j += 1) {
        const a = nodes[i]
        const b = nodes[j]

        const ax = Math.cos(a.theta) * a.radius
        const ay = Math.sin(a.theta * 0.7) * 0.75
        const az = Math.sin(a.theta) * a.radius

        const bx = Math.cos(b.theta) * b.radius
        const by = Math.sin(b.theta * 0.7) * 0.75
        const bz = Math.sin(b.theta) * b.radius

        const distance = Math.hypot(ax - bx, ay - by, az - bz)
        candidates.push({ i, j, distance })
      }
    }

    const degrees = Array(nodes.length).fill(0)
    const edges = candidates
      .sort((a, b) => a.distance - b.distance)
      .filter(({ i, j }) => {
        if (degrees[i] >= MAX_EDGES_PER_NODE || degrees[j] >= MAX_EDGES_PER_NODE) return false
        degrees[i] += 1
        degrees[j] += 1
        return true
      })
      .map((edge, index) => ({
        ...edge,
        packetSeed: index * 0.37,
      }))

    let width = 0
    let height = 0
    let frame = 0
    let lastTime = 0
    let elapsed = 0
    let visible = true
    let disposed = false

    function project(x, y, z) {
      const scale = camera / (camera - z)
      return { x: x * scale, y: y * scale, scale }
    }

    function updateWorld(centerX, centerY, spreadX, spreadY, delta) {
      const ease = 1 - Math.exp(-delta * 5)
      const release = 1 - Math.exp(-delta * 3.5)

      for (const node of nodes) {
        const orbit = elapsed * (0.1 + node.depthBias * 0.05)
        const angle = node.theta + orbit

        // Make a 3D-ish cloud with a little cylindrical / spherical feel.
        const rx = Math.cos(angle) * node.radius
        const rz = Math.sin(angle) * node.radius
        const ry =
          Math.sin(node.theta * 1.3 + elapsed * 0.22 + node.phase * 0.12) * (0.24 + node.band * 0.34)

        // Depth layering: farther nodes recede into screen, closer nodes come forward.
        const zDepth =
          rz * 260 +
          (node.depthBias - 0.5) * 180 +
          Math.sin(elapsed * 0.33 + node.phase) * 16

        const xDepth = rx * spreadX
        const yDepth = ry * spreadY

        node.wx = xDepth
        node.wy = yDepth
        node.wz = zDepth

        const projected = project(node.wx, node.wy, node.wz)
        const baseX = centerX + projected.x
        const baseY = centerY + projected.y

        node.scale = projected.scale

        const distance = Math.hypot(pointer.x - baseX, pointer.y - baseY)
        const targetAttached = pointer.active && distance < 220 ? 1 : 0
        node.attachment += (targetAttached - node.attachment) * (targetAttached ? ease : release)

        const attraction = Math.max(0, 1 - distance / 240) * node.attachment
        node.pullX += ((pointer.x - baseX) * attraction * 0.18 - node.pullX) * ease
        node.pullY += ((pointer.y - baseY) * attraction * 0.18 - node.pullY) * ease

        node.x = baseX + node.pullX
        node.y = baseY + node.pullY
      }
    }

    function drawBackground(centerX, centerY, spreadX) {
      const halo = context.createRadialGradient(centerX, centerY, 0, centerX, centerY, spreadX * 1.1)
      halo.addColorStop(0, 'rgba(95, 153, 137, 0.055)')
      halo.addColorStop(0.55, 'rgba(95, 153, 137, 0.02)')
      halo.addColorStop(1, 'rgba(95, 153, 137, 0)')
      context.fillStyle = halo
      context.fillRect(0, 0, width, height)

      // Faint horizontal data lane feel.
      const lane = context.createLinearGradient(0, centerY, 0, centerY + height * 0.22)
      lane.addColorStop(0, 'rgba(40, 79, 74, 0)')
      lane.addColorStop(0.45, 'rgba(40, 79, 74, 0.04)')
      lane.addColorStop(1, 'rgba(40, 79, 74, 0)')
      context.fillStyle = lane
      context.fillRect(0, centerY - height * 0.12, width, height * 0.24)
    }

    function drawEdges() {
      edges.forEach(({ i, j, packetSeed }, index) => {
        const a = nodes[i]
        const b = nodes[j]

        const avgScale = (a.scale + b.scale) / 2
        const depthAlpha = Math.max(0.08, Math.min(0.34, (avgScale - 0.72) * 0.24))
        const highlight = Math.max(a.attachment, b.attachment)
        const alpha = depthAlpha + highlight * 0.12

        context.beginPath()
        context.moveTo(a.x, a.y)
        context.lineTo(b.x, b.y)
        context.strokeStyle = `rgba(151, 187, 180, ${alpha})`
        context.lineWidth = 0.35 + avgScale * 0.55
        context.stroke()

        // Occasional travelling packet — ETL / data flow vibe.
        if (index % 7 === 0) {
          const cycle = (elapsed * (0.15 + (index % 5) * 0.01) + packetSeed) % 2.8
          if (cycle < 1) {
            const head = cycle
            const tail = Math.max(0, cycle - 0.08)
            const fade = Math.sin(cycle * Math.PI)

            const x1 = a.x + (b.x - a.x) * tail
            const y1 = a.y + (b.y - a.y) * tail
            const x2 = a.x + (b.x - a.x) * head
            const y2 = a.y + (b.y - a.y) * head

            context.beginPath()
            context.moveTo(x1, y1)
            context.lineTo(x2, y2)
            context.strokeStyle = `rgba(187, 237, 218, ${fade * (0.24 + avgScale * 0.18)})`
            context.lineWidth = 1.1 + avgScale * 0.45
            context.stroke()
          }
        }
      })
    }

    function drawPointerRelations() {
      if (!(pointer.strength > 0.01 || nodes.some((node) => node.attachment > 0.01))) return

      const linked = nodes
        .filter((node) => node.attachment > 0.01)
        .map((node) => ({
          node,
          distance: Math.hypot(node.x - pointer.x, node.y - pointer.y),
        }))

      linked.forEach(({ node, distance }) => {
        const strength = Math.max(0, 1 - distance / 300) * node.attachment
        const dx = pointer.x - node.x
        const dy = pointer.y - node.y
        const bend = Math.sin(elapsed * 0.9 + node.phase) * Math.min(distance * 0.022, 4)
        const normalX = -dy / Math.max(distance, 1)
        const normalY = dx / Math.max(distance, 1)

        const endX = node.x + dx * node.attachment
        const endY = node.y + dy * node.attachment
        const controlX = (node.x + endX) / 2 + normalX * bend * node.attachment
        const controlY = (node.y + endY) / 2 + normalY * bend * node.attachment

        context.beginPath()
        context.moveTo(node.x, node.y)
        context.quadraticCurveTo(controlX, controlY, endX, endY)
        context.strokeStyle = `rgba(151, 187, 180, ${node.attachment * 0.18 + strength * 0.34})`
        context.lineWidth = 0.95 + strength * 0.55
        context.stroke()
      })

      context.beginPath()
      context.arc(pointer.x, pointer.y, 2.4 + Math.sin(elapsed * 1.25) * 0.35, 0, Math.PI * 2)
      context.fillStyle = `rgba(182, 233, 215, ${pointer.strength * 0.82})`
      context.shadowColor = '#9fe1c7'
      context.shadowBlur = 12
      context.fill()
      context.shadowBlur = 0
    }

    function drawNodes() {
      // Draw back-to-front for stronger depth illusion.
      ;[...nodes]
        .sort((a, b) => a.scale - b.scale)
        .forEach((node) => {
          const light = node.attachment
          const major = node.scale > 1.05

          if (major || light > 0.05) {
            context.beginPath()
            context.arc(node.x, node.y, 5.5 * node.scale + light * 2.8, 0, Math.PI * 2)
            context.strokeStyle = `rgba(158, 207, 191, ${0.035 + light * 0.2})`
            context.lineWidth = 0.65 + node.scale * 0.2
            context.stroke()
          }

          context.beginPath()
          context.arc(node.x, node.y, 0.9 + node.scale * 1.45 + light * 0.6, 0, Math.PI * 2)
          context.fillStyle = `rgba(177, 220, 204, ${0.1 + node.scale * 0.28 + light * 0.18})`
          context.shadowColor = '#a6dcc7'
          context.shadowBlur = 6 + light * 8
          context.fill()
          context.shadowBlur = 0
        })
    }

    function draw(time) {
      frame = 0
      if (disposed) return

      const animate = !paused && !motion.matches && visible && !document.hidden
      const delta = animate && lastTime ? Math.min((time - lastTime) / 1000, 0.04) : 0
      lastTime = time
      elapsed += delta

      context.clearRect(0, 0, width, height)

      const centerX = width * (width > 700 ? 0.67 : 0.5)
      const centerY = height * (width > 700 ? 0.52 : 0.62)
      const spreadX = Math.min(width * 0.33, 360)
      const spreadY = height * 0.28

      const ease = 1 - Math.exp(-delta * 5)
      pointer.strength += ((pointer.active && animate ? 1 : 0) - pointer.strength) * ease

      drawBackground(centerX, centerY, spreadX)
      updateWorld(centerX, centerY, spreadX, spreadY, delta)
      drawEdges()
      drawPointerRelations()
      drawNodes()

      if (animate) frame = requestAnimationFrame(draw)
    }

    function schedule() {
      if (!frame && !disposed) frame = requestAnimationFrame(draw)
    }

    function resize() {
      const bounds = canvas.getBoundingClientRect()
      width = bounds.width
      height = bounds.height

      const ratio = Math.min(window.devicePixelRatio || 1, 2)
      canvas.width = Math.round(width * ratio)
      canvas.height = Math.round(height * ratio)
      context.setTransform(ratio, 0, 0, ratio, 0, 0)

      schedule()
    }

    function move(event) {
      if (paused || motion.matches || event.pointerType === 'touch') return
      const bounds = canvas.getBoundingClientRect()
      pointer.x = event.clientX - bounds.left
      pointer.y = event.clientY - bounds.top
      pointer.active = true
      schedule()
    }

    function leave() {
      pointer.active = false
    }

    function resume() {
      lastTime = 0
      pointer.active = false
      schedule()
    }

    const resizeObserver = new ResizeObserver(resize)
    const intersectionObserver = new IntersectionObserver(([entry]) => {
      visible = entry.isIntersecting
      resume()
    })

    resizeObserver.observe(canvas)
    intersectionObserver.observe(canvas)
    canvas.addEventListener('pointermove', move)
    canvas.addEventListener('pointerleave', leave)
    canvas.addEventListener('pointercancel', leave)
    motion.addEventListener('change', resume)
    document.addEventListener('visibilitychange', resume)
    resize()

    return () => {
      disposed = true
      cancelAnimationFrame(frame)
      resizeObserver.disconnect()
      intersectionObserver.disconnect()
      canvas.removeEventListener('pointermove', move)
      canvas.removeEventListener('pointerleave', leave)
      canvas.removeEventListener('pointercancel', leave)
      motion.removeEventListener('change', resume)
      document.removeEventListener('visibilitychange', resume)
    }
  }, [paused])

  return <canvas ref={canvasRef} className="home-network" aria-hidden="true" />
}