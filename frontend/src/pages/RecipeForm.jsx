import { useState, useEffect } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import api from '../api/axios'
import styles from './RecipeForm.module.css'

function UrlExtractor({ onExtracted }) {
  const [url, setUrl] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const handleExtract = async e => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const res = await api.post('/recipes/extract', { url })
      onExtracted(res.data.data)
    } catch (err) {
      setError(err.response?.data?.message || 'URL에서 레시피를 가져올 수 없습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <section className={styles.extractBox}>
      <div className={styles.extractHeader}>
        <span className={styles.extractIcon}>✨</span>
        <div>
          <h2>URL로 자동 추출</h2>
          <p>블로그, 유튜브 등 SNS 링크를 붙여넣으면 AI가 레시피를 자동으로 채워드려요.</p>
        </div>
      </div>
      <form onSubmit={handleExtract} className={styles.extractForm}>
        <input
          type="url"
          placeholder="https://www.youtube.com/watch?v=... 또는 블로그 URL"
          value={url}
          onChange={e => setUrl(e.target.value)}
          required
        />
        <button type="submit" className="btn-primary" disabled={loading}>
          {loading ? '분석 중...' : 'AI 추출'}
        </button>
      </form>
      {loading && <p className={styles.extractLoading}>🤖 AI가 레시피를 분석하고 있어요. 잠시만 기다려주세요...</p>}
      {error && <p className="error-msg">{error}</p>}
    </section>
  )
}

const AGE_GROUPS = [
  { value: 'MONTH_4_6', label: '4~6개월' },
  { value: 'MONTH_7_9', label: '7~9개월' },
  { value: 'MONTH_10_12', label: '10~12개월' },
  { value: 'MONTH_12_18', label: '12~18개월' },
  { value: 'MONTH_18_PLUS', label: '18개월 이상' },
]

const CATEGORIES = [
  { value: 'PORRIDGE', label: '죽' },
  { value: 'SOUP', label: '국/찌개' },
  { value: 'SIDE_DISH', label: '반찬' },
  { value: 'FINGER_FOOD', label: '핑거푸드' },
  { value: 'SNACK', label: '간식' },
  { value: 'DRINK', label: '음료' },
]

const emptyIngredient = () => ({ name: '', amount: '', unit: '' })
const emptyStep = (order) => ({ order, description: '', imageUrl: '' })

export default function RecipeForm() {
  const { id } = useParams()
  const navigate = useNavigate()
  const isEdit = !!id

  const [form, setForm] = useState({
    title: '', description: '', ageGroup: 'MONTH_4_6', category: 'PORRIDGE',
    cookingTime: '', servings: '', imageUrl: '', tags: '',
    ingredients: [emptyIngredient()],
    steps: [emptyStep(1)],
  })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const handleExtracted = (data) => {
    setForm(f => ({
      ...f,
      title: data.title || f.title,
      description: data.description || f.description,
      ageGroup: data.ageGroup || f.ageGroup,
      category: data.category || f.category,
      cookingTime: data.cookingTime ?? f.cookingTime,
      servings: data.servings ?? f.servings,
      imageUrl: data.imageUrl || f.imageUrl,
      tags: data.tags?.join(', ') || f.tags,
      ingredients: data.ingredients?.length ? data.ingredients : f.ingredients,
      steps: data.steps?.length ? data.steps : f.steps,
    }))
  }

  useEffect(() => {
    if (isEdit) {
      api.get(`/recipes/${id}`).then(res => {
        const r = res.data.data
        setForm({
          title: r.title || '',
          description: r.description || '',
          ageGroup: r.ageGroup || 'MONTH_4_6',
          category: r.category || 'PORRIDGE',
          cookingTime: r.cookingTime || '',
          servings: r.servings || '',
          imageUrl: r.imageUrl || '',
          tags: r.tags?.join(', ') || '',
          ingredients: r.ingredients?.length ? r.ingredients : [emptyIngredient()],
          steps: r.steps?.length ? r.steps : [emptyStep(1)],
        })
      })
    }
  }, [id, isEdit])

  const setField = (key, value) => setForm(f => ({ ...f, [key]: value }))

  const updateIngredient = (i, key, value) => {
    setForm(f => {
      const arr = [...f.ingredients]
      arr[i] = { ...arr[i], [key]: value }
      return { ...f, ingredients: arr }
    })
  }

  const updateStep = (i, key, value) => {
    setForm(f => {
      const arr = [...f.steps]
      arr[i] = { ...arr[i], [key]: value }
      return { ...f, steps: arr }
    })
  }

  const handleSubmit = async e => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const body = {
        title: form.title,
        description: form.description,
        ageGroup: form.ageGroup,
        category: form.category,
        cookingTime: form.cookingTime ? Number(form.cookingTime) : null,
        servings: form.servings ? Number(form.servings) : null,
        imageUrl: form.imageUrl || null,
        tags: form.tags ? form.tags.split(',').map(t => t.trim()).filter(Boolean) : [],
        ingredients: form.ingredients.filter(i => i.name),
        steps: form.steps.filter(s => s.description).map((s, idx) => ({ ...s, order: idx + 1 })),
      }
      if (isEdit) {
        await api.put(`/recipes/${id}`, body)
        navigate(`/recipes/${id}`)
      } else {
        const res = await api.post('/recipes', body)
        navigate(`/recipes/${res.data.data.id}`)
      }
    } catch (err) {
      setError(err.response?.data?.message || '저장에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="page">
      <div className={`container ${styles.wrap}`}>
        <h1>{isEdit ? '레시피 수정' : '레시피 작성'}</h1>
        {!isEdit && <UrlExtractor onExtracted={handleExtracted} />}
        <form onSubmit={handleSubmit} className={styles.form}>

          <section className={styles.section}>
            <h2>기본 정보</h2>
            <label>제목 *<input value={form.title} onChange={e => setField('title', e.target.value)} required maxLength={100} /></label>
            <label>설명<textarea value={form.description} onChange={e => setField('description', e.target.value)} rows={3} maxLength={500} /></label>
            <div className={styles.row}>
              <label>연령대 *
                <select value={form.ageGroup} onChange={e => setField('ageGroup', e.target.value)}>
                  {AGE_GROUPS.map(ag => <option key={ag.value} value={ag.value}>{ag.label}</option>)}
                </select>
              </label>
              <label>카테고리 *
                <select value={form.category} onChange={e => setField('category', e.target.value)}>
                  {CATEGORIES.map(c => <option key={c.value} value={c.value}>{c.label}</option>)}
                </select>
              </label>
            </div>
            <div className={styles.row}>
              <label>조리시간(분)<input type="number" value={form.cookingTime} onChange={e => setField('cookingTime', e.target.value)} min={1} /></label>
              <label>인분<input type="number" value={form.servings} onChange={e => setField('servings', e.target.value)} min={1} /></label>
            </div>
            <label>이미지 URL<input value={form.imageUrl} onChange={e => setField('imageUrl', e.target.value)} placeholder="https://..." /></label>
            <label>태그 (쉼표로 구분)<input value={form.tags} onChange={e => setField('tags', e.target.value)} placeholder="이유식, 간식, 쉬운레시피" /></label>
          </section>

          <section className={styles.section}>
            <div className={styles.sectionHeader}>
              <h2>재료</h2>
              <button type="button" className="btn-secondary btn-sm" onClick={() => setForm(f => ({ ...f, ingredients: [...f.ingredients, emptyIngredient()] }))}>+ 재료 추가</button>
            </div>
            {form.ingredients.map((ing, i) => (
              <div key={i} className={styles.ingredientRow}>
                <input placeholder="재료명" value={ing.name} onChange={e => updateIngredient(i, 'name', e.target.value)} />
                <input placeholder="양" value={ing.amount} onChange={e => updateIngredient(i, 'amount', e.target.value)} style={{width:'80px'}} />
                <input placeholder="단위" value={ing.unit} onChange={e => updateIngredient(i, 'unit', e.target.value)} style={{width:'70px'}} />
                {form.ingredients.length > 1 && (
                  <button type="button" className="btn-danger btn-sm" onClick={() => setForm(f => ({ ...f, ingredients: f.ingredients.filter((_, j) => j !== i) }))}>✕</button>
                )}
              </div>
            ))}
          </section>

          <section className={styles.section}>
            <div className={styles.sectionHeader}>
              <h2>만드는 법</h2>
              <button type="button" className="btn-secondary btn-sm" onClick={() => setForm(f => ({ ...f, steps: [...f.steps, emptyStep(f.steps.length + 1)] }))}>+ 단계 추가</button>
            </div>
            {form.steps.map((step, i) => (
              <div key={i} className={styles.stepRow}>
                <span className={styles.stepNum}>{i + 1}</span>
                <textarea
                  placeholder={`${i + 1}단계 설명`}
                  value={step.description}
                  onChange={e => updateStep(i, 'description', e.target.value)}
                  rows={2}
                />
                {form.steps.length > 1 && (
                  <button type="button" className="btn-danger btn-sm" onClick={() => setForm(f => ({ ...f, steps: f.steps.filter((_, j) => j !== i) }))}>✕</button>
                )}
              </div>
            ))}
          </section>

          {error && <p className="error-msg">{error}</p>}
          <div className={styles.submit}>
            <button type="button" className="btn-secondary" onClick={() => navigate(-1)}>취소</button>
            <button type="submit" className="btn-primary" disabled={loading}>{loading ? '저장 중...' : isEdit ? '수정 완료' : '레시피 등록'}</button>
          </div>
        </form>
      </div>
    </div>
  )
}
