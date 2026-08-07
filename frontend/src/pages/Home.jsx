import { useState, useEffect, useCallback } from 'react'
import api from '../api/axios'
import RecipeCard from '../components/RecipeCard'
import styles from './Home.module.css'

const AGE_GROUPS = [
  { value: '', label: '전체' },
  { value: 'BABY_FOOD', label: '이유식' },
  { value: 'ADULT_FOOD', label: '어른 음식' },
]

const CATEGORIES = [
  { value: '', label: '전체 카테고리' },
  { value: 'PORRIDGE', label: '죽' },
  { value: 'SOUP', label: '국/찌개' },
  { value: 'SIDE_DISH', label: '반찬' },
  { value: 'FINGER_FOOD', label: '핑거푸드' },
  { value: 'SNACK', label: '간식' },
  { value: 'DRINK', label: '음료' },
]

export default function Home() {
  const [recipes, setRecipes] = useState([])
  const [filters, setFilters] = useState({ ageGroup: '', category: '', keyword: '' })
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(false)

  const fetchRecipes = useCallback(async (f, p) => {
    setLoading(true)
    try {
      const params = { page: p, size: 12 }
      if (f.ageGroup) params.ageGroup = f.ageGroup
      if (f.category) params.category = f.category
      if (f.keyword) params.keyword = f.keyword
      const res = await api.get('/recipes', { params })
      setRecipes(res.data.data.content)
      setTotalPages(res.data.data.totalPages)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchRecipes(filters, page)
  }, [filters, page, fetchRecipes])

  const handleFilter = (key, value) => {
    setPage(0)
    setFilters(f => ({ ...f, [key]: value }))
  }

  const handleSearch = e => {
    e.preventDefault()
    setPage(0)
    setFilters(f => ({ ...f, keyword }))
  }

  return (
    <div className="page">
      <div className="container">
        <div className={styles.header}>
          <h1>📖 reciplog</h1>
          <form onSubmit={handleSearch} className={styles.searchForm}>
            <input
              placeholder="레시피 검색..."
              value={keyword}
              onChange={e => setKeyword(e.target.value)}
            />
            <button type="submit" className="btn-primary">검색</button>
          </form>
        </div>

        <div className={styles.filters}>
          <div className={styles.filterGroup}>
            {AGE_GROUPS.map(ag => (
              <button
                key={ag.value}
                className={filters.ageGroup === ag.value ? styles.active : styles.filterBtn}
                onClick={() => handleFilter('ageGroup', ag.value)}
              >
                {ag.label}
              </button>
            ))}
          </div>
          <div className={styles.filterGroup}>
            {CATEGORIES.map(cat => (
              <button
                key={cat.value}
                className={filters.category === cat.value ? styles.active : styles.filterBtn}
                onClick={() => handleFilter('category', cat.value)}
              >
                {cat.label}
              </button>
            ))}
          </div>
        </div>

        {loading ? (
          <p className={styles.loading}>불러오는 중...</p>
        ) : recipes.length === 0 ? (
          <p className={styles.empty}>등록된 레시피가 없습니다.</p>
        ) : (
          <div className={styles.grid}>
            {recipes.map(r => <RecipeCard key={r.id} recipe={r} />)}
          </div>
        )}

        {totalPages > 1 && (
          <div className={styles.pagination}>
            <button onClick={() => setPage(p => p - 1)} disabled={page === 0} className="btn-secondary">이전</button>
            <span>{page + 1} / {totalPages}</span>
            <button onClick={() => setPage(p => p + 1)} disabled={page >= totalPages - 1} className="btn-secondary">다음</button>
          </div>
        )}
      </div>
    </div>
  )
}
