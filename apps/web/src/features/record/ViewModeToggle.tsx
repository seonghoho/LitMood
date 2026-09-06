'use client'

import { css } from 'styled-system/css'
import { flex } from 'styled-system/patterns'
import { VIEW_MODE_LABEL, type ViewMode } from './view-mode'

const MODES: ViewMode[] = ['list', 'grid']

/** 리스트 ↔ 그리드 전환 (F-04-03). 키보드만으로도 넘어갈 수 있어야 한다 (NFR-05). */
export function ViewModeToggle({
  mode,
  onChange,
}: {
  mode: ViewMode
  onChange: (next: ViewMode) => void
}) {
  return (
    <div className={flex({ gap: '1' })} role="group" aria-label="목록 표시 방식">
      {MODES.map((value) => (
        <button
          key={value}
          type="button"
          onClick={() => onChange(value)}
          aria-pressed={mode === value}
          className={css({
            textStyle: 'caption',
            px: '3',
            py: '1',
            rounded: 'full',
            cursor: 'pointer',
            borderWidth: '1px',
            borderStyle: 'solid',
            borderColor: mode === value ? 'brand.default' : 'border.default',
            bg: mode === value ? 'brand.default' : 'bg.surface',
            color: mode === value ? 'fg.onAccent' : 'fg.muted',
          })}
        >
          {VIEW_MODE_LABEL[value]}
        </button>
      ))}
    </div>
  )
}
