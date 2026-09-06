import { css } from 'styled-system/css'

/** 카드의 부가 동작(수정·삭제). 리스트 카드와 그리드 카드가 같은 모양을 쓴다. */
export function CardAction({
  onClick,
  label,
  danger,
  children,
}: {
  onClick: () => void
  /** 카드가 여러 개라 "수정" 만으로는 무엇을 수정하는지 알 수 없다 */
  label: string
  danger?: boolean
  children: React.ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      className={css({
        textStyle: 'caption',
        px: '2',
        py: '1',
        rounded: 'sm',
        cursor: 'pointer',
        bg: 'transparent',
        color: danger ? 'danger.500' : 'fg.muted',
        _hover: { bg: 'bg.subtle' },
      })}
    >
      {children}
    </button>
  )
}
