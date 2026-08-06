import { useState, useEffect, useRef } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import api from '../api/axios'
import { sourceLabel, isLinkOnly } from '../utils/sourceLink'
import styles from './RecipeForm.module.css'

// 이미지 파일을 업로드하고 URL을 반환하는 공용 컴포넌트
function ImageUploadInput({ value, onChange, placeholder = '사진 추가' }) {
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState('')
  const inputRef = useRef(null)

  const handleFile = async e => {
    const file = e.target.files[0]
    if (!file) return
    setError('')
    setUploading(true)
    try {
      const formData = new FormData()
      formData.append('image', file)
      const res = await api.post('/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      onChange(res.data.data.imageUrl)
    } catch (err) {
      setError(err.response?.data?.message || '이미지 업로드에 실패했습니다.')
    } finally {
      setUploading(false)
      e.target.value = ''
    }
  }

  const handleRemove = () => {
    onChange('')
    setError('')
  }

  return (
    <div className={styles.imageUpload}>
      {value ? (
        <div className={styles.imagePreviewWrap}>
          <img
            src={value}
            alt="미리보기"
            className={styles.imagePreview}
            onClick={() => inputRef.current?.click()}
            title="클릭하여 이미지 교체"
          />
          <button type="button" className={styles.imageRemoveBtn} onClick={handleRemove}>✕</button>
          {uploading && <div className={styles.imageUploading}>교체 중...</div>}
        </div>
      ) : (
        <button
          type="button"
          className={styles.imageAddBtn}
          onClick={() => inputRef.current?.click()}
          disabled={uploading}
        >
          {uploading ? '업로드 중...' : `+ ${placeholder}`}
        </button>
      )}
      <input
        ref={inputRef}
        type="file"
        accept="image/jpeg,image/png,image/webp"
        style={{ display: 'none' }}
        onChange={handleFile}
      />
      {error && <p className={styles.imageError}>{error}</p>}
    </div>
  )
}

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
    <div className={styles.extractTab}>
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
      {loading && <p className={styles.extractLoading}>AI가 레시피를 분석하고 있어요. 잠시만 기다려주세요...</p>}
      {error && <p className="error-msg">{error}</p>}
    </div>
  )
}

function ImageExtractor({ onExtracted }) {
  const [files, setFiles] = useState([])
  const [previews, setPreviews] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const handleFileChange = e => {
    const selected = Array.from(e.target.files)
    if (selected.length > 10) {
      setError('이미지는 최대 10장까지 선택 가능합니다.')
      return
    }
    setError('')
    setFiles(selected)
    setPreviews(selected.map(f => URL.createObjectURL(f)))
  }

  const handleExtract = async e => {
    e.preventDefault()
    if (files.length === 0) {
      setError('이미지를 1장 이상 선택해주세요.')
      return
    }
    setError('')
    setLoading(true)
    try {
      const formData = new FormData()
      files.forEach(f => formData.append('images', f))
      const res = await api.post('/recipes/extract/images', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      onExtracted(res.data.data)
    } catch (err) {
      setError(err.response?.data?.message || '이미지에서 레시피를 가져올 수 없습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className={styles.extractTab}>
      <form onSubmit={handleExtract} className={styles.imageExtractForm}>
        <label className={styles.fileLabel}>
          사진 선택 (최대 10장, jpg/png/webp)
          <input
            type="file"
            accept="image/jpeg,image/png,image/webp"
            multiple
            onChange={handleFileChange}
          />
        </label>
        {previews.length > 0 && (
          <div className={styles.previewGrid}>
            {previews.map((src, i) => (
              <img key={i} src={src} alt={`미리보기 ${i + 1}`} className={styles.previewImg} />
            ))}
          </div>
        )}
        <button type="submit" className="btn-primary" disabled={loading || files.length === 0}>
          {loading ? '분석 중...' : 'AI 추출'}
        </button>
      </form>
      {loading && <p className={styles.extractLoading}>AI가 사진을 분석하고 있어요. 잠시만 기다려주세요...</p>}
      {error && <p className="error-msg">{error}</p>}
    </div>
  )
}

function ExtractedRecipeList({ recipes, onSelect, onFinish, onSaveAll, saving, saveError }) {
  const savedCount = recipes.filter(r => r._saved).length
  const remaining = recipes.length - savedCount
  return (
    <div className={styles.extractedList}>
      <p className={styles.extractedListInfo}>
        레시피 {recipes.length}개를 찾았어요. 등록할 항목을 선택해 확인 후 저장하거나, 한 번에 모두 등록할 수 있어요. ({savedCount}/{recipes.length} 등록됨)
      </p>
      <div className={styles.cardGrid}>
        {recipes.map((r, i) => {
          const linkOnly = isLinkOnly(r)
          return (
          <div key={i} className={linkOnly ? `${styles.recipeCard} ${styles.linkOnlyCard}` : styles.recipeCard}>
            {linkOnly ? (
              <div className={styles.linkOnlyThumb}>🔗</div>
            ) : (
              r.imageUrl && <img src={r.imageUrl} alt="" className={styles.recipeCardImg} />
            )}
            <h3>{r.title}</h3>
            {linkOnly ? (
              <p className={styles.recipeCardDesc}>자동 추출 실패 · 링크만 저장됨</p>
            ) : (
              r.description && <p className={styles.recipeCardDesc}>{r.description}</p>
            )}
            {r._saved ? (
              <span className={styles.savedBadge}>✅ 등록 완료</span>
            ) : (
              <button type="button" className="btn-primary btn-sm" onClick={() => onSelect(i)} disabled={saving}>확인하기</button>
            )}
          </div>
          )
        })}
      </div>
      {saveError && <p className="error-msg">{saveError}</p>}
      <div className={styles.extractedListActions}>
        <button type="button" className="btn-primary" onClick={onSaveAll} disabled={saving || remaining === 0}>
          {saving ? '등록 중...' : `전체 등록 (${remaining}개 남음)`}
        </button>
        <button type="button" className="btn-secondary" onClick={onFinish} disabled={saving}>완료하고 나가기</button>
      </div>
    </div>
  )
}

function AutoExtractor({ onExtracted }) {
  const [tab, setTab] = useState('url')

  return (
    <section className={styles.extractBox}>
      <div className={styles.extractHeader}>
        <span className={styles.extractIcon}>✨</span>
        <div>
          <h2>AI로 자동 추출</h2>
          <p>URL이나 사진을 올리면 AI가 레시피를 자동으로 채워드려요.</p>
        </div>
      </div>
      <div className={styles.extractTabs}>
        <button
          type="button"
          className={tab === 'url' ? styles.activeTab : styles.inactiveTab}
          onClick={() => setTab('url')}
        >
          URL로 추출
        </button>
        <button
          type="button"
          className={tab === 'image' ? styles.activeTab : styles.inactiveTab}
          onClick={() => setTab('image')}
        >
          사진으로 추출
        </button>
      </div>
      {tab === 'url' ? <UrlExtractor onExtracted={onExtracted} /> : <ImageExtractor onExtracted={onExtracted} />}
    </section>
  )
}

const AGE_GROUPS = [
  { value: 'BABY_FOOD', label: '이유식' },
  { value: 'ADULT_FOOD', label: '어른 음식' },
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

const blankForm = () => ({
  title: '', description: '', ageGroup: 'BABY_FOOD', category: 'PORRIDGE',
  cookingTime: '', servings: '', imageUrl: '', sourceUrl: '', tags: '',
  ingredients: [emptyIngredient()],
  steps: [emptyStep(1)],
})

const mergeIntoForm = (base, data) => ({
  ...base,
  title: data.title || base.title,
  description: data.description || base.description,
  ageGroup: data.ageGroup || base.ageGroup,
  category: data.category || base.category,
  cookingTime: data.cookingTime ?? base.cookingTime,
  servings: data.servings ?? base.servings,
  imageUrl: base.imageUrl || data.imageUrl,
  sourceUrl: data.sourceUrl || base.sourceUrl,
  tags: data.tags?.join(', ') || base.tags,
  ingredients: data.ingredients?.length ? data.ingredients : base.ingredients,
  steps: data.steps?.length ? data.steps : base.steps,
})

export default function RecipeForm() {
  const { id } = useParams()
  const navigate = useNavigate()
  const isEdit = !!id

  const [form, setForm] = useState(blankForm())
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  // 사진/URL에서 레시피가 여러 개 추출됐을 때: 목록에서 고른 항목만 확인 후 저장
  const [extractedList, setExtractedList] = useState(null)
  const [activeIndex, setActiveIndex] = useState(null)
  const [savingAll, setSavingAll] = useState(false)
  const [saveAllError, setSaveAllError] = useState('')
  const [linkOnlyNotice, setLinkOnlyNotice] = useState(false)

  const handleExtracted = (data) => {
    const list = Array.isArray(data) ? data : [data]
    if (list.length > 1) {
      setExtractedList(list.map(r => ({ ...r, _saved: false })))
      setActiveIndex(null)
      setLinkOnlyNotice(false)
    } else if (list.length === 1) {
      setLinkOnlyNotice(isLinkOnly(list[0]))
      setForm(f => mergeIntoForm(f, list[0]))
    }
  }

  const openForEdit = (i) => {
    setForm(mergeIntoForm(blankForm(), extractedList[i]))
    setLinkOnlyNotice(isLinkOnly(extractedList[i]))
    setActiveIndex(i)
  }

  const backToList = () => setActiveIndex(null)

  const finishExtractedList = () => navigate('/')

  const buildCreateBody = (r) => ({
    title: r.title,
    description: r.description || '',
    ageGroup: r.ageGroup,
    category: r.category,
    cookingTime: r.cookingTime ?? null,
    servings: r.servings ?? null,
    imageUrl: r.imageUrl || null,
    sourceUrl: r.sourceUrl || null,
    tags: r.tags || [],
    ingredients: (r.ingredients || []).filter(i => i.name),
    steps: (r.steps || []).filter(s => s.description).map((s, idx) => ({ ...s, order: idx + 1 })),
  })

  const saveAllExtracted = async () => {
    setSaveAllError('')
    setSavingAll(true)
    const failedTitles = []
    for (let i = 0; i < extractedList.length; i++) {
      const r = extractedList[i]
      if (r._saved) continue
      try {
        await api.post('/recipes', buildCreateBody(r))
        setExtractedList(list => list.map((item, idx) => idx === i ? { ...item, _saved: true } : item))
      } catch (err) {
        failedTitles.push(r.title || `#${i + 1}`)
      }
    }
    setSavingAll(false)
    if (failedTitles.length) {
      setSaveAllError(`다음 레시피는 등록에 실패했어요: ${failedTitles.join(', ')}`)
    }
  }

  useEffect(() => {
    if (isEdit) {
      api.get(`/recipes/${id}`).then(res => {
        const r = res.data.data
        const LEGACY_TO_NEW = {
          MONTH_4_6: 'BABY_FOOD',
          MONTH_7_9: 'BABY_FOOD',
          MONTH_10_12: 'BABY_FOOD',
          MONTH_12_18: 'BABY_FOOD',
          MONTH_18_PLUS: 'ADULT_FOOD',
        }
        setForm({
          title: r.title || '',
          description: r.description || '',
          ageGroup: LEGACY_TO_NEW[r.ageGroup] ?? r.ageGroup ?? 'BABY_FOOD',
          category: r.category || 'PORRIDGE',
          cookingTime: r.cookingTime || '',
          servings: r.servings || '',
          imageUrl: r.imageUrl || '',
          sourceUrl: r.sourceUrl || '',
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
        sourceUrl: form.sourceUrl || null,
        tags: form.tags ? form.tags.split(',').map(t => t.trim()).filter(Boolean) : [],
        ingredients: form.ingredients.filter(i => i.name),
        steps: form.steps.filter(s => s.description).map((s, idx) => ({ ...s, order: idx + 1 })),
      }
      if (isEdit) {
        await api.put(`/recipes/${id}`, body)
        navigate(`/recipes/${id}`)
      } else {
        const res = await api.post('/recipes', body)
        if (extractedList && activeIndex !== null) {
          const savedIndex = activeIndex
          setExtractedList(list => list.map((r, i) => i === savedIndex ? { ...r, _saved: true } : r))
          setForm(blankForm())
          setActiveIndex(null)
        } else {
          navigate(`/recipes/${res.data.data.id}`)
        }
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
        {!isEdit && !extractedList && <AutoExtractor onExtracted={handleExtracted} />}
        {extractedList && activeIndex === null ? (
          <ExtractedRecipeList
            recipes={extractedList}
            onSelect={openForEdit}
            onFinish={finishExtractedList}
            onSaveAll={saveAllExtracted}
            saving={savingAll}
            saveError={saveAllError}
          />
        ) : (
        <form onSubmit={handleSubmit} className={styles.form}>
          {extractedList && (
            <button type="button" className={styles.backToListBtn} onClick={backToList}>← 목록으로</button>
          )}

          {linkOnlyNotice && (
            <div className={styles.linkOnlyNotice}>
              <span className={styles.linkOnlyNoticeIcon}>🔗</span>
              <p>자동으로 읽어올 수 없는 링크예요. 링크는 저장해뒀으니 아래에서 나머지 정보만 채워주세요!</p>
            </div>
          )}

          <section className={styles.section}>
            <h2>기본 정보</h2>
            {form.sourceUrl && (
              <a href={form.sourceUrl} target="_blank" rel="noopener noreferrer" className={styles.sourceLinkChip}>
                {sourceLabel(form.sourceUrl)} 원본 링크 열기 ↗
              </a>
            )}
            <div>
              <p className={styles.fieldLabel}>대표 이미지</p>
              <ImageUploadInput
                value={form.imageUrl}
                onChange={url => setField('imageUrl', url)}
                placeholder="대표 이미지 업로드"
              />
            </div>
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
                <div className={styles.stepContent}>
                  <textarea
                    placeholder={`${i + 1}단계 설명`}
                    value={step.description}
                    onChange={e => updateStep(i, 'description', e.target.value)}
                    rows={2}
                  />
                  <ImageUploadInput
                    value={step.imageUrl}
                    onChange={url => updateStep(i, 'imageUrl', url)}
                    placeholder="단계 사진 추가 (선택)"
                  />
                </div>
                {form.steps.length > 1 && (
                  <button type="button" className="btn-danger btn-sm" onClick={() => setForm(f => ({ ...f, steps: f.steps.filter((_, j) => j !== i) }))}>✕</button>
                )}
              </div>
            ))}
          </section>

          {error && <p className="error-msg">{error}</p>}
          <div className={styles.submit}>
            <button type="button" className="btn-secondary" onClick={() => extractedList ? backToList() : navigate(-1)}>취소</button>
            <button type="submit" className="btn-primary" disabled={loading}>{loading ? '저장 중...' : isEdit ? '수정 완료' : '레시피 등록'}</button>
          </div>
        </form>
        )}
      </div>
    </div>
  )
}
