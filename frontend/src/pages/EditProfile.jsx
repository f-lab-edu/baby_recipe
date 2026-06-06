import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../api/axios'
import { useAuth } from '../contexts/AuthContext'
import styles from './EditProfile.module.css'

export default function EditProfile() {
  const navigate = useNavigate()
  const { user: me, updateUser } = useAuth()

  const [form, setForm] = useState({ nickname: '', bio: '' })
  const [pwForm, setPwForm] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' })
  const [errors, setErrors] = useState({})
  const [loading, setLoading] = useState(false)
  const [successMsg, setSuccessMsg] = useState('')

  useEffect(() => {
    if (!me) return
    api.get(`/users/${me.id}`).then(res => {
      const { nickname, bio } = res.data.data
      setForm({ nickname: nickname || '', bio: bio || '' })
    })
  }, [me])

  const validate = () => {
    const errs = {}
    if (!form.nickname.trim()) errs.nickname = '닉네임을 입력해주세요.'
    else if (form.nickname.length < 2 || form.nickname.length > 20) errs.nickname = '닉네임은 2~20자여야 합니다.'
    if (form.bio.length > 200) errs.bio = '자기소개는 200자 이하여야 합니다.'

    if (pwForm.newPassword) {
      if (!pwForm.currentPassword) errs.currentPassword = '현재 비밀번호를 입력해주세요.'
      if (pwForm.newPassword.length < 8) errs.newPassword = '새 비밀번호는 8자 이상이어야 합니다.'
      if (pwForm.newPassword !== pwForm.confirmPassword) errs.confirmPassword = '새 비밀번호가 일치하지 않습니다.'
    }
    return errs
  }

  const handleSubmit = async e => {
    e.preventDefault()
    const errs = validate()
    if (Object.keys(errs).length > 0) { setErrors(errs); return }

    setLoading(true)
    setErrors({})
    try {
      const payload = { nickname: form.nickname, bio: form.bio }
      if (pwForm.newPassword) {
        payload.currentPassword = pwForm.currentPassword
        payload.newPassword = pwForm.newPassword
      }
      const res = await api.put('/users/me', payload)
      updateUser(res.data.data)
      setSuccessMsg('저장되었습니다.')
      setPwForm({ currentPassword: '', newPassword: '', confirmPassword: '' })
      setTimeout(() => navigate(`/users/${me.id}`), 800)
    } catch (err) {
      const msg = err.response?.data?.message || '저장에 실패했습니다.'
      setErrors({ server: msg })
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="page">
      <div className="container">
        <div className={styles.wrap}>
          <h2 className={styles.title}>회원 정보 수정</h2>

          <form onSubmit={handleSubmit} noValidate>
            <section className={styles.section}>
              <h3 className={styles.sectionTitle}>기본 정보</h3>

              <div className={styles.field}>
                <label>닉네임</label>
                <input
                  value={form.nickname}
                  onChange={e => setForm(f => ({ ...f, nickname: e.target.value }))}
                  placeholder="닉네임 (2~20자)"
                  maxLength={20}
                />
                {errors.nickname && <p className="error-msg">{errors.nickname}</p>}
              </div>

              <div className={styles.field}>
                <label>자기소개</label>
                <textarea
                  value={form.bio}
                  onChange={e => setForm(f => ({ ...f, bio: e.target.value }))}
                  placeholder="자기소개를 입력해주세요 (200자 이하)"
                  rows={3}
                  maxLength={200}
                />
                <p className={styles.charCount}>{form.bio.length} / 200</p>
                {errors.bio && <p className="error-msg">{errors.bio}</p>}
              </div>
            </section>

            <section className={styles.section}>
              <h3 className={styles.sectionTitle}>비밀번호 변경</h3>
              <p className={styles.sectionDesc}>변경하지 않으려면 비워두세요.</p>

              <div className={styles.field}>
                <label>현재 비밀번호</label>
                <input
                  type="password"
                  value={pwForm.currentPassword}
                  onChange={e => setPwForm(f => ({ ...f, currentPassword: e.target.value }))}
                  placeholder="현재 비밀번호"
                  autoComplete="current-password"
                />
                {errors.currentPassword && <p className="error-msg">{errors.currentPassword}</p>}
              </div>

              <div className={styles.field}>
                <label>새 비밀번호</label>
                <input
                  type="password"
                  value={pwForm.newPassword}
                  onChange={e => setPwForm(f => ({ ...f, newPassword: e.target.value }))}
                  placeholder="새 비밀번호 (8자 이상)"
                  autoComplete="new-password"
                />
                {errors.newPassword && <p className="error-msg">{errors.newPassword}</p>}
              </div>

              <div className={styles.field}>
                <label>새 비밀번호 확인</label>
                <input
                  type="password"
                  value={pwForm.confirmPassword}
                  onChange={e => setPwForm(f => ({ ...f, confirmPassword: e.target.value }))}
                  placeholder="새 비밀번호를 다시 입력해주세요"
                  autoComplete="new-password"
                />
                {errors.confirmPassword && <p className="error-msg">{errors.confirmPassword}</p>}
                {!errors.confirmPassword && pwForm.confirmPassword && pwForm.newPassword === pwForm.confirmPassword && (
                  <p className={styles.matchMsg}>비밀번호가 일치합니다.</p>
                )}
              </div>
            </section>

            {errors.server && <p className="error-msg" style={{ marginBottom: 12 }}>{errors.server}</p>}
            {successMsg && <p className={styles.successMsg}>{successMsg}</p>}

            <div className={styles.actions}>
              <button type="button" className="btn-secondary" onClick={() => navigate(`/users/${me?.id}`)}>취소</button>
              <button type="submit" className="btn-primary" disabled={loading}>
                {loading ? '저장 중...' : '저장'}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  )
}
