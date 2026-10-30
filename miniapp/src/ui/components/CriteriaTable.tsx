import { useState } from 'react'
import type { Criterion, CriterionState } from '../../data/schema'
import type { CriteriaDictionary } from '../../lib/criteria'

const STATE_ICON: Record<CriterionState, string> = {
  pass: '✓',
  warn: '!',
  fail: '✕',
  'n/a': '–',
}

const STATE_CLASS: Record<CriterionState, string> = {
  pass: 'state-pass',
  warn: 'state-warn',
  fail: 'state-fail',
  'n/a': 'state-na',
}

interface Props {
  criteria: Criterion[]
  dictionary: CriteriaDictionary
}

/** Таблица C1–C8 и P1–P3. Тап по строке раскрывает расшифровку из CRITERIA.md. */
export function CriteriaTable({ criteria, dictionary }: Props) {
  const [openCode, setOpenCode] = useState<string | null>(null)

  if (criteria.length === 0) {
    return <div className="small muted">Критерии не заполнены.</div>
  }

  return (
    <div>
      {criteria.map((criterion) => {
        const help = dictionary[criterion.code]
        const isOpen = openCode === criterion.code
        return (
          <div key={criterion.code}>
            <button
              type="button"
              className="criteria-row"
              aria-expanded={isOpen}
              onClick={() => setOpenCode(isOpen ? null : criterion.code)}
            >
              <span className="code">{criterion.code}</span>
              <span className={STATE_CLASS[criterion.status]} aria-label={criterion.status}>
                {STATE_ICON[criterion.status]}
              </span>
              <span className="note">{criterion.note || '—'}</span>
            </button>
            {isOpen && (
              <div className="criteria-help">
                {help ? (
                  <>
                    <b>{help.title}</b>
                    {help.text && `\n${help.text}`}
                  </>
                ) : (
                  'Расшифровка для этого кода не найдена в CRITERIA.md.'
                )}
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}
