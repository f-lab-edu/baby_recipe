import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import styles from './Navbar.module.css'

export default function Navbar() {
  const { user, logout, isLoggedIn } = useAuth()
  const navigate = useNavigate()

  const handleLogout = async () => {
    await logout()
    navigate('/login')
  }

  return (
    <nav className={styles.nav}>
      <div className={`container ${styles.inner}`}>
        <Link to="/" className={styles.logo}>📖 reciplog</Link>
        <div className={styles.links}>
          {isLoggedIn ? (
            <>
              <Link to="/feed">피드</Link>
              <Link to="/chat">채팅</Link>
              <Link to="/recipes/new">레시피 작성</Link>
              <Link to={`/users/${user?.id}`}>{user?.nickname || '마이페이지'}</Link>
              <button className="btn-secondary btn-sm" onClick={handleLogout}>로그아웃</button>
            </>
          ) : (
            <>
              <Link to="/login">로그인</Link>
              <Link to="/register">
                <button className="btn-primary btn-sm">회원가입</button>
              </Link>
            </>
          )}
        </div>
      </div>
    </nav>
  )
}
