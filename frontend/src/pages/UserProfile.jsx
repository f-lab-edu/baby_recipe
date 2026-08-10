import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import api from '../api/axios'
import { useAuth } from '../contexts/AuthContext'
import RecipeCard from '../components/RecipeCard'
import FollowListModal from '../components/FollowListModal'
import styles from './UserProfile.module.css'

export default function UserProfile() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { user: me, isLoggedIn } = useAuth()
  const [profile, setProfile] = useState(null)
  const [recipes, setRecipes] = useState([])
  const [tab, setTab] = useState('recipes')
  const [loading, setLoading] = useState(true)
  const [followModal, setFollowModal] = useState(null) // null | 'followers' | 'following'

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

  const startChat = async () => {
    if (!isLoggedIn) { navigate('/login'); return }
    try {
      const res = await api.post('/chat/rooms', { partnerId: Number(id) })
      navigate(`/chat/${res.data.data.id}`)
    } catch (err) {
      alert(err.response?.data?.message || '채팅을 시작할 수 없습니다.')
    }
  }

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
            <h2>{profile.nickname}</h2>
            {profile.bio && <p className={styles.bio}>{profile.bio}</p>}
            <div className={styles.stats}>
              <span><b>{profile.recipeCount ?? recipes.length}</b> 레시피</span>
              <span className={styles.statClickable} onClick={() => setFollowModal('followers')}>
                <b>{profile.followerCount ?? 0}</b> 팔로워
              </span>
              <span className={styles.statClickable} onClick={() => setFollowModal('following')}>
                <b>{profile.followingCount ?? 0}</b> 팔로잉
              </span>
            </div>
            {isMe ? (
              <button className="btn-secondary btn-sm" onClick={() => navigate('/profile/edit')}>프로필 수정</button>
            ) : isLoggedIn && (
              <div className={styles.actions}>
                <button className={profile.following ? 'btn-secondary btn-sm' : 'btn-primary btn-sm'} onClick={toggleFollow}>
                  {profile.following ? '팔로잉' : '팔로우'}
                </button>
                <button className="btn-secondary btn-sm" onClick={startChat}>채팅하기</button>
              </div>
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

      {followModal && (
        <FollowListModal
          userId={id}
          mode={followModal}
          onClose={() => setFollowModal(null)}
        />
      )}
    </div>
  )
}
