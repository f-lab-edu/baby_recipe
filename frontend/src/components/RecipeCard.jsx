import { Link } from 'react-router-dom'
import { isLinkOnly } from '../utils/sourceLink'
import styles from './RecipeCard.module.css'

export default function RecipeCard({ recipe }) {
  const linkOnly = isLinkOnly(recipe)
  return (
    <Link to={`/recipes/${recipe.id}`} className={styles.card}>
      <div className={styles.thumb}>
        {linkOnly && <span className={styles.linkBadge}>🔗 링크</span>}
        {linkOnly ? (
          <div className={styles.linkThumb}>🔗</div>
        ) : recipe.imageUrl ? (
          <img src={recipe.imageUrl} alt={recipe.title} />
        ) : (
          <div className={styles.placeholder}>🍱</div>
        )}
      </div>
      <div className={styles.body}>
        <div className={styles.badges}>
          <span className={styles.badge}>{recipe.ageGroupLabel}</span>
          <span className={styles.badge}>{recipe.categoryLabel}</span>
        </div>
        <h3 className={styles.title}>{recipe.title}</h3>
        <p className={styles.desc}>{recipe.description}</p>
        <div className={styles.meta}>
          <span>👤 {recipe.author?.nickname}</span>
          <span>❤️ {recipe.likeCount}</span>
          <span>💬 {recipe.commentCount}</span>
          {recipe.cookingTime && <span>⏱ {recipe.cookingTime}분</span>}
        </div>
      </div>
    </Link>
  )
}
