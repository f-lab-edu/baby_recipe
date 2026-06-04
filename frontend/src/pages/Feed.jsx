import { useState, useEffect } from 'react'
import api from '../api/axios'
import RecipeCard from '../components/RecipeCard'
import styles from './Home.module.css'

export default function Feed() {
  const [recipes, setRecipes] = useState([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    api.get('/feed', { params: { page, size: 12 } })
      .then(res => {
        setRecipes(res.data.data.content)
        setTotalPages(res.data.data.totalPages)
      })
      .finally(() => setLoading(false))
  }, [page])

  return (
    <div className="page">
      <div className="container">
        <h1 style={{ fontSize: '22px', fontWeight: 700, marginBottom: '24px' }}>📰 팔로우 피드</h1>
        {loading ? (
          <p className={styles.loading}>불러오는 중...</p>
        ) : recipes.length === 0 ? (
          <p className={styles.empty}>팔로우한 유저의 레시피가 없습니다.<br />다른 유저를 팔로우해보세요!</p>
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
