type Props = {
  className?: string
  title?: string
}

export function BioQCLogo({ className, title = 'BioQC' }: Props) {
  return (
    <svg
      viewBox="0 0 64 64"
      role="img"
      aria-label={title}
      className={className}
      xmlns="http://www.w3.org/2000/svg"
    >
      <defs>
        <linearGradient id="bioqc-grad" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#06b6d4" />
          <stop offset="55%" stopColor="#10b981" />
          <stop offset="100%" stopColor="#6366f1" />
        </linearGradient>
      </defs>
      <rect x="2" y="2" width="60" height="60" rx="14" fill="url(#bioqc-grad)" />
      <text
        x="50%"
        y="56%"
        textAnchor="middle"
        dominantBaseline="middle"
        fontFamily="Inter, system-ui, sans-serif"
        fontWeight="800"
        fontSize="30"
        fill="white"
        letterSpacing="-1"
      >
        BQ
      </text>
    </svg>
  )
}
