import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import api from '../api/axios'
import { useAuth } from '../contexts/AuthContext'
import RecipeCard from '../components/RecipeCard'
import styles from './UserProfile.module.css'

export default function UserProfile() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { user: me, isLoggedIn, updateUser } = useAuth()
  const [profile, setProfile] = useState(null)
  const [recipes, setRecipes] = useState([])
  const [tab, setTab] = useState('recipes')
  const [editMode, setEditMode] = useState(false)
  const [editForm, setEditForm] = useState({ nickname: '', bio: '' })
  const [loading, setLoading] = useState(true)

  const isMe = me?.id === Number(id)

  useEffect(() => {
    setLoading(true)
    Promise.all([
      api.get(`/users/${id}`),
      api.get('/recipes', { params: { page: 0, size: 20 } }),
    ]).then(([uRes, rRes]) => {
      setProfile(uRes.data.data)
      const all = rRes.data.data.content
      setRecipes(all.filter(r => r.author?.id === Number(id)))
    }).finally(() => setLoading(false))
  }, [id])

  const toggleFollow = async () => {
    if (!isLoggedIn) { navigate('/login'); return }
    if (profile.following) {
      await api.delete(`/users/${id}/follow`)
      setProfile(p => ({ ...p, following: false, followerCount: p.followerCount - 1 }))
    } else {
      await api.post(`/users/${id}/follow`)
      setProfile(p => ({ ...p, following: true, followerCount: p.followerCount + 1 }))
    }
  }

  const saveProfile = async e => {
    e.preventDefault()
    const res = await api.put('/users/me', editForm)
    setProfile(p => ({ ...p, ...res.data.data }))
    updateUser(res.data.data)
    setEditMode(false)
  }

  if (loading) return <p style={{ textAlign: 'center', padding: '60px', color: '#868e96' }}>불러오는 중...</p>
  if (!profile) return null

  return (
    <div className="page">
      <div className="container">
        <div className={styles.profileCard}>
          <div className={styles.avatar}>
            {profile.profileImage ? <img src={profile.profileImage} alt="" /> : <span>👤</span>}
          </div>
          <div className={styles.info}>
            {editMode ? (
              <form onSubmit={saveProfile} className={styles.editForm}>
                <input value={editForm.nickname} onChange={e => setEditForm(f => ({ ...f, nickname: e.target.value }))} placeholder="닉네임" required />
                <textarea value={editForm.bio} onChange={e => setEditForm(f => ({ ...f, bio: e.target.value }))} placeholder="자기소개" rows={2} />
                <div className={styles.editActions}>
                  <button type="button" className="btn-secondary btn-sm" onClick={() => setEditMode(false)}>취소</button>
                  <button type="submit" className="btn-primary btn-sm">저장</button>
                </div>
              </form>
            ) : (
              <>
                <h2>{profile.nickname}</h2>
                {profile.bio && <p className={styles.bio}>{profile.bio}</p>}
                <div className={styles.stats}>
                  <span><b>{profile.recipeCount ?? recipes.length}</b> 레시피</span>
                  <span><b>{profile.followerCount ?? 0}</b> 팔로워</span>
                  <span><b>{profile.followingCount ?? 0}</b> 팔로잉</span>
                </div>
                {isMe ? (
                  <button className="btn-secondary btn-sm" onClick={() => { setEditMode(true); setEditForm({ nickname: profile.nickname, bio: profile.bio || '' }) }}>프로필 수정</button>
                ) : isLoggedIn && (
                  <button className={profile.following ? 'btn-secondary btn-sm' : 'btn-primary btn-sm'} onClick={toggleFollow}>
                    {profile.following ? '팔로잉' : '팔로우'}
                  </button>
                )}
              </>
            )}
          </div>
        </div>

        <div className={styles.tabs}>
          <button className={tab === 'recipes' ? styles.activeTab : styles.tab} onClick={() => setTab('recipes')}>레시피</button>
        </div>

        {recipes.length === 0 ? (
          <p style={{ textAlign: 'center', padding: '40px', color: '#868e96' }}>작성한 레시피가 없습니다.</p>
        ) : (
          <div className={styles.grid}>
            {recipes.map(r => <RecipeCard key={r.id} recipe={r} />)}
          </div>
        )}
      </div>
    </div>
  )
}
