import { useState, useEffect } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import api from '../api/axios'
import { useAuth } from '../contexts/AuthContext'
import { hostnameOf, sourceLabel } from '../utils/sourceLink'
import styles from './RecipeDetail.module.css'

export default function RecipeDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { user, isLoggedIn } = useAuth()
  const [recipe, setRecipe] = useState(null)
  const [comments, setComments] = useState([])
  const [commentText, setCommentText] = useState('')
  const [loading, setLoading] = useState(true)
  const [zoomImage, setZoomImage] = useState(null)

  useEffect(() => {
    if (!zoomImage) return
    const onKeyDown = e => { if (e.key === 'Escape') setZoomImage(null) }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [zoomImage])

  useEffect(() => {
    Promise.all([
      api.get(`/recipes/${id}`),
      api.get(`/recipes/${id}/comments`)
    ]).then(([rRes, cRes]) => {
      setRecipe(rRes.data.data)
      setComments(cRes.data.data.content)
    }).finally(() => setLoading(false))
  }, [id])

  const toggleLike = async () => {
    if (!isLoggedIn) { navigate('/login'); return }
    const res = await api.post(`/recipes/${id}/like`)
    setRecipe(r => ({ ...r, liked: res.data.data, likeCount: r.likeCount + (res.data.data ? 1 : -1) }))
  }

  const toggleBookmark = async () => {
    if (!isLoggedIn) { navigate('/login'); return }
    const res = await api.post(`/recipes/${id}/bookmark`)
    setRecipe(r => ({ ...r, bookmarked: res.data.data }))
  }

  const submitComment = async e => {
    e.preventDefault()
    if (!isLoggedIn) { navigate('/login'); return }
    const res = await api.post(`/recipes/${id}/comments`, { content: commentText })
    setComments(c => [res.data.data, ...c])
    setCommentText('')
  }

  const deleteComment = async commentId => {
    await api.delete(`/recipes/${id}/comments/${commentId}`)
    setComments(c => c.filter(cm => cm.id !== commentId))
  }

  const deleteRecipe = async () => {
    if (!window.confirm('레시피를 삭제하시겠습니까?')) return
    await api.delete(`/recipes/${id}`)
    navigate('/')
  }

  if (loading) return <p className={styles.loading}>불러오는 중...</p>
  if (!recipe) return <p className={styles.loading}>레시피를 찾을 수 없습니다.</p>

  const isAuthor = user?.id === recipe.author?.id

  return (
    <div className="page">
      <div className={`container ${styles.wrap}`}>
        <div className={styles.main}>
          {recipe.imageUrl && (
            <img src={recipe.imageUrl} alt={recipe.title} className={styles.image}
              onClick={() => setZoomImage(recipe.imageUrl)} />
          )}

          <div className={styles.badges}>
            <span className={styles.badge}>{recipe.ageGroupLabel}</span>
            <span className={styles.badge}>{recipe.categoryLabel}</span>
          </div>

          <div className={styles.titleRow}>
            <h1>{recipe.title}</h1>
            {isAuthor && (
              <div className={styles.actions}>
                <Link to={`/recipes/${id}/edit`}><button className="btn-secondary btn-sm">수정</button></Link>
                <button className="btn-danger btn-sm" onClick={deleteRecipe}>삭제</button>
              </div>
            )}
          </div>

          <div className={styles.authorRow}>
            <Link to={`/users/${recipe.author?.id}`} className={styles.author}>
              👤 {recipe.author?.nickname}
            </Link>
            <span className={styles.meta}>
              👁 {recipe.viewCount} · ⏱ {recipe.cookingTime ? `${recipe.cookingTime}분` : '-'} · 🍽 {recipe.servings ? `${recipe.servings}인분` : '-'}
            </span>
          </div>

          {recipe.sourceUrl && (
            <a href={recipe.sourceUrl} target="_blank" rel="noopener noreferrer" className={styles.sourceLinkCard}>
              <span>{sourceLabel(recipe.sourceUrl)}</span>
              <span className={styles.sourceDomain}>{hostnameOf(recipe.sourceUrl)}</span>
              <span>원문 보러가기 →</span>
            </a>
          )}

          <p className={styles.desc}>{recipe.description}</p>

          <div className={styles.reactionRow}>
            <button onClick={toggleLike} className={recipe.liked ? styles.liked : styles.reactionBtn}>
              ❤️ {recipe.likeCount}
            </button>
            <button onClick={toggleBookmark} className={recipe.bookmarked ? styles.bookmarked : styles.reactionBtn}>
              🔖 {recipe.bookmarked ? '저장됨' : '저장'}
            </button>
          </div>

          {recipe.tags?.length > 0 && (
            <div className={styles.tags}>
              {recipe.tags.map(t => <span key={t} className={styles.tag}>#{t}</span>)}
            </div>
          )}

          {recipe.ingredients?.length > 0 && (
            <section className={styles.section}>
              <h2>재료</h2>
              <ul className={styles.ingredients}>
                {recipe.ingredients.map((ing, i) => (
                  <li key={i}><span>{ing.name}</span><span>{ing.amount} {ing.unit}</span></li>
                ))}
              </ul>
            </section>
          )}

          {recipe.steps?.length > 0 && (
            <section className={styles.section}>
              <h2>만드는 법</h2>
              <ol className={styles.steps}>
                {recipe.steps.map(step => (
                  <li key={step.order}>
                    <span className={styles.stepNum}>{step.order}</span>
                    <div className={styles.stepContent}>
                      <p>{step.description}</p>
                      {step.imageUrl && (
                        <img src={step.imageUrl} alt={`${step.order}단계`} className={styles.stepImage}
                          onClick={() => setZoomImage(step.imageUrl)} />
                      )}
                    </div>
                  </li>
                ))}
              </ol>
            </section>
          )}

          {isAuthor && recipe.sourceUrl && !recipe.ingredients?.length && !recipe.steps?.length && (
            <div className={styles.emptyContentHint}>
              <span>아직 재료·조리법이 없어요. 원문을 참고해서 채워보세요!</span>
              <Link to={`/recipes/${id}/edit`}><button className="btn-secondary btn-sm">수정하기</button></Link>
            </div>
          )}

          <section className={styles.section}>
            <h2>댓글 {comments.length}</h2>
            {isLoggedIn && (
              <form onSubmit={submitComment} className={styles.commentForm}>
                <textarea
                  placeholder="댓글을 입력하세요..."
                  value={commentText}
                  onChange={e => setCommentText(e.target.value)}
                  rows={2} required
                />
                <button type="submit" className="btn-primary btn-sm">등록</button>
              </form>
            )}
            <ul className={styles.comments}>
              {comments.map(cm => (
                <li key={cm.id}>
                  <div className={styles.commentHeader}>
                    <span className={styles.commentAuthor}>{cm.authorNickname}</span>
                    <span className={styles.commentDate}>{cm.createdAt?.slice(0, 10)}</span>
                    {user?.id === cm.authorId && (
                      <button className="btn-sm" style={{color:'#fa5252', background:'none'}} onClick={() => deleteComment(cm.id)}>삭제</button>
                    )}
                  </div>
                  <p>{cm.content}</p>
                </li>
              ))}
            </ul>
          </section>
        </div>
      </div>

      {zoomImage && (
        <div className={styles.lightbox} onClick={() => setZoomImage(null)}>
          <button className={styles.lightboxClose} aria-label="닫기">✕</button>
          <img src={zoomImage} alt="확대 이미지" onClick={e => e.stopPropagation()} />
        </div>
      )}
    </div>
  )
}
