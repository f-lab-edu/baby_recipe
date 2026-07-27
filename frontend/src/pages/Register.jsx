import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import api from '../api/axios'
import styles from './Auth.module.css'

export default function Register() {
  const navigate = useNavigate()
  const [form, setForm] = useState({ email: '', password: '', nickname: '' })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const handleSubmit = async e => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      await api.post('/auth/register', form)
      navigate('/login')
    } catch (err) {
      setError(err.response?.data?.message || '회원가입에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className={styles.wrap}>
      <div className={styles.box}>
        <h2 className={styles.title}>📖 reciplog</h2>
        <p className={styles.sub}>회원가입</p>
        <form onSubmit={handleSubmit} className={styles.form}>
          <input
            type="email" placeholder="이메일"
            value={form.email}
            onChange={e => setForm(f => ({ ...f, email: e.target.value }))}
            required
          />
          <input
            type="password" placeholder="비밀번호 (8자 이상)"
            value={form.password}
            onChange={e => setForm(f => ({ ...f, password: e.target.value }))}
            required minLength={8}
          />
          <input
            type="text" placeholder="닉네임 (2~20자)"
            value={form.nickname}
            onChange={e => setForm(f => ({ ...f, nickname: e.target.value }))}
            required minLength={2} maxLength={20}
          />
          {error && <p className="error-msg">{error}</p>}
          <button type="submit" className="btn-primary" disabled={loading}>
            {loading ? '처리 중...' : '가입하기'}
          </button>
        </form>
        <p className={styles.footer}>
          이미 계정이 있으신가요? <Link to="/login">로그인</Link>
        </p>
      </div>
    </div>
  )
}
